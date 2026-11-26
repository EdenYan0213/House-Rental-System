package com.house.ai;

import com.house.model.Bill;
import com.house.model.ContractStatus;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.MeterReading;
import com.house.model.RemindLog;
import com.house.model.Room;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import com.house.service.MeterReadingBatchService;
import com.house.service.SmsService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 意图的业务实现：抄表、催缴短信、查合同、查用电量。
 *
 * <p>从 1054 行的 {@code AiAssistantService} 抽出。模型只负责「选哪个工具」，
 * 真正读写数据全在这里，所以这里的方法才是审计与测试的重点。
 */
@Component
public class AiIntentHandlers {

  private final AiAssistantProperties properties;
  private final AiEntityResolver resolver;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final MeterReadingRepository meterReadingRepository;
  private final MeterReadingBatchService meterReadingBatchService;
  private final SmsService smsService;

  public AiIntentHandlers(AiAssistantProperties properties,
      AiEntityResolver resolver,
      RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      MeterReadingRepository meterReadingRepository,
      MeterReadingBatchService meterReadingBatchService,
      SmsService smsService) {
    this.properties = properties;
    this.resolver = resolver;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.meterReadingRepository = meterReadingRepository;
    this.meterReadingBatchService = meterReadingBatchService;
    this.smsService = smsService;
  }

  // ---------------- 抄表 ----------------

  /** 单条抄表：补齐默认值后复用批量逻辑，避免两套校验。 */
  public AiAssistantResponse meterReading(Map<String, Object> entities) {
    String roomNumber = resolver.getText(entities, "room_number");
    Double waterReading = resolver.getDouble(entities, "water_reading");
    Double electricReading = resolver.getDouble(entities, "electric_reading");
    if (!resolver.hasText(roomNumber)) {
      return AiAssistantResponse.fail("缺少房间号，请补充例如\"101房间\"。", "METER_READING");
    }
    if (waterReading == null) {
      return AiAssistantResponse.fail("缺少水表读数，请补充水表数值。", "METER_READING");
    }
    if (electricReading == null) {
      return AiAssistantResponse.fail("缺少电表读数，请补充电表数值。", "METER_READING");
    }

    Map<String, Object> row = new HashMap<>();
    row.put("roomNumber", roomNumber);
    row.put("waterReading", waterReading);
    row.put("electricReading", electricReading);
    row.put("date", resolver.getText(entities, "date"));
    row.put("buildingName", resolver.getText(entities, "building_name"));

    Map<String, Object> batchEntities = new HashMap<>();
    batchEntities.put("records", List.of(row));
    batchEntities.put("date", resolver.getText(entities, "date"));
    batchEntities.put("building_name", resolver.getText(entities, "building_name"));
    return meterReadingBatch(batchEntities);
  }

  /** 批量抄表：逐条校验 → 解析房间 → 一次性 upsert。 */
  public AiAssistantResponse meterReadingBatch(Map<String, Object> entities) {
    List<Map<String, Object>> records = resolver.toMapList(entities.get("records"));
    if (records.isEmpty()) {
      return AiAssistantResponse.fail("缺少抄表数据，请按“房间号+水表+电表”提供至少1条记录。", "METER_READING");
    }

    LocalDate defaultDate = resolver.parseDateOrDefault(resolver.getText(entities, "date"), LocalDate.now());
    String defaultBuildingName = resolver.getText(entities, "building_name");

    List<ResolvedMeterRecord> resolved = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> row = records.get(i);
      int rowNo = i + 1;

      String roomNumber = resolver.getTextAny(row, "roomNumber", "room_number");
      if (!resolver.hasText(roomNumber)) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少房间号，请补充。", "METER_READING");
      }

      Double waterReading = resolver.getDoubleAny(row, "waterReading", "water_reading");
      if (waterReading == null) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少水表读数，请补充。", "METER_READING");
      }

      Double electricReading = resolver.getDoubleAny(row, "electricReading", "electric_reading");
      if (electricReading == null) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少电表读数，请补充。", "METER_READING");
      }
      if (waterReading < 0 || electricReading < 0) {
        return AiAssistantResponse.fail("第" + rowNo + "条读数不能为负数。", "METER_READING");
      }

      LocalDate rowDate = resolver.parseDateOrDefault(
          resolver.getTextAny(row, "date", "recordDate", "record_date"), defaultDate);
      String rowBuilding = resolver.getTextAny(row, "buildingName", "building_name");
      if (!resolver.hasText(rowBuilding)) {
        rowBuilding = defaultBuildingName;
      }

      AiEntityResolver.RoomMatch match = resolver.resolveRoom(roomNumber, rowBuilding);
      if (match.isAmbiguous()) {
        AiAssistantResponse clarify = AiAssistantResponse.fail(match.message(), "METER_READING");
        clarify.setRequiresClarification(true);
        clarify.setSuggestions(match.suggestions());
        return clarify;
      }
      if (!match.isFound()) {
        return AiAssistantResponse.fail(match.message(), "METER_READING");
      }

      Room room = match.room();
      Building building = room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);
      String displayRoom = (building == null ? "" : building.getName()) + room.getRoomNumber();

      resolved.add(new ResolvedMeterRecord(room, rowDate, waterReading, electricReading, displayRoom));
    }

    try {
      List<MeterReadingBatchService.MeterUpsertItem> items = resolved.stream()
          .map(r -> new MeterReadingBatchService.MeterUpsertItem(
              r.room().getId(), r.date(), r.waterReading(), r.electricReading()))
          .toList();
      meterReadingBatchService.upsertBatch(items);
    } catch (IllegalArgumentException ex) {
      // 业务校验（如读数倒退）的提示直接透出给用户
      return AiAssistantResponse.fail(ex.getMessage(), "METER_READING");
    }

    if (resolved.size() == 1) {
      ResolvedMeterRecord one = resolved.get(0);
      AiAssistantResponse response = AiAssistantResponse.ok(
          "房间 " + one.displayRoom() + " 水电读数录入成功（水：" + one.waterReading()
              + "，电：" + one.electricReading() + "）",
          "METER_READING");
      response.setDetailLink("/meter-readings?date=" + one.date()
          + "&buildingId=" + one.room().getBuildingId());
      return response;
    }

    String sample = resolved.stream().limit(3).map(ResolvedMeterRecord::displayRoom)
        .collect(Collectors.joining("、"));
    AiAssistantResponse response = AiAssistantResponse.ok(
        "已批量录入" + resolved.size() + "条水电读数（示例：" + sample + "）。", "METER_READING");

    // 同一天同一楼栋才给精确跳转，否则落到列表页
    Set<LocalDate> dates = resolved.stream().map(ResolvedMeterRecord::date).collect(Collectors.toSet());
    Set<Long> buildingIds = resolved.stream()
        .map(r -> r.room().getBuildingId()).collect(Collectors.toSet());
    if (dates.size() == 1 && buildingIds.size() == 1) {
      response.setDetailLink("/meter-readings?date=" + resolved.get(0).date()
          + "&buildingId=" + resolved.get(0).room().getBuildingId());
    } else {
      response.setDetailLink("/meter-readings");
    }
    return response;
  }

  // ---------------- 催缴短信 ----------------

  public AiAssistantResponse sendSms(Map<String, Object> entities) {
    String tenantName = resolver.getText(entities, "tenant_name");
    if (!resolver.hasText(tenantName)) {
      return AiAssistantResponse.fail("缺少租户姓名，请补充后重试。", "SEND_SMS");
    }

    AiEntityResolver.TenantMatch match = resolver.resolveTenant(tenantName, entities, true);
    if (match.isAmbiguous()) {
      AiAssistantResponse clarify = AiAssistantResponse.fail(match.message(), "SEND_SMS");
      clarify.setRequiresClarification(true);
      clarify.setSuggestions(match.suggestions());
      return clarify;
    }
    if (!match.isFound()) {
      return AiAssistantResponse.fail(match.message(), "SEND_SMS");
    }

    AiEntityResolver.TenantContract ctx = match.context();
    if (ctx.contract() == null) {
      return AiAssistantResponse.fail("未找到进行中的合同，无法发送催缴短信。", "SEND_SMS");
    }

    List<Bill> overdueBills = billRepository.findByContractId(ctx.contract().getId()).stream()
        .filter(resolver::isPendingStatus)
        .filter(b -> overdueDays(b) > 0)
        .toList();

    if (overdueBills.isEmpty()) {
      return AiAssistantResponse.ok("该租户暂无逾期账单。", "SEND_SMS");
    }
    if (!resolver.hasText(ctx.tenant().getPhone())) {
      return AiAssistantResponse.fail("该租户没有手机号，无法发送短信。", "SEND_SMS");
    }

    int maxOverdueDays = overdueBills.stream().mapToInt(this::overdueDays).max().orElse(0);
    double total = overdueBills.stream()
        .mapToDouble(b -> b.getTotalAmount() == null ? 0 : b.getTotalAmount()).sum();
    String content = properties.getSmsOverdueTemplate()
        .replace("{租户姓名}", resolver.safe(ctx.tenant().getName()))
        .replace("{房间号}", resolver.safe(ctx.room() == null ? null : ctx.room().getRoomNumber()))
        .replace("{逾期笔数}", String.valueOf(overdueBills.size()))
        .replace("{总欠费金额}", String.format(Locale.ROOT, "%.2f", total))
        .replace("{逾期天数}", String.valueOf(maxOverdueDays));

    RemindLog log = RemindLog.builder()
        .tenantId(ctx.tenant().getId())
        .tenantName(ctx.tenant().getName())
        .phone(ctx.tenant().getPhone())
        .roomNumber(ctx.room() == null ? null : ctx.room().getRoomNumber())
        .overdueDays(maxOverdueDays)
        .billIds(overdueBills.stream().map(Bill::getId).collect(Collectors.toList()).toString())
        .totalAmount(total)
        .content(content)
        .build();

    RemindLog sent = smsService.sendSms(log);
    boolean success = "success".equalsIgnoreCase(sent.getStatus());
    String message = success
        ? "账单短信已发送给" + ctx.tenant().getName()
        : "短信发送失败：" + resolver.safe(sent.getFailReason());

    AiAssistantResponse response = success
        ? AiAssistantResponse.ok(message, "SEND_SMS")
        : AiAssistantResponse.fail(message, "SEND_SMS");
    response.setDetailLink("/bills?status=PENDING&roomId="
        + (ctx.room() == null ? "" : ctx.room().getId()));
    return response;
  }

  // ---------------- 查合同 ----------------

  public AiAssistantResponse queryContract(Map<String, Object> entities) {
    String tenantName = resolver.getText(entities, "tenant_name");
    if (!resolver.hasText(tenantName)) {
      return AiAssistantResponse.fail("缺少租户姓名，请补充后重试。", "QUERY_CONTRACT");
    }

    List<com.house.model.Tenant> tenants = resolver.resolveTenantCandidates(tenantName);
    if (tenants.isEmpty()) {
      return AiAssistantResponse.fail("未找到租户\"" + tenantName + "\"，请确认姓名是否正确。", "QUERY_CONTRACT");
    }
    if (tenants.size() > 1) {
      return buildTenantClarification("QUERY_CONTRACT", tenantName, tenants);
    }

    com.house.model.Tenant tenant = tenants.get(0);
    List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
    if (contracts.isEmpty()) {
      return AiAssistantResponse.ok("租户\"" + tenantName + "\"暂无合同记录。", "QUERY_CONTRACT");
    }

    contracts.sort(Comparator.comparing(Contract::getStartDate,
        Comparator.nullsLast(Comparator.reverseOrder())));

    // 一次取出相关房间，避免逐条查询
    Map<Long, Room> roomMap = new HashMap<>();
    List<Long> roomIds = contracts.stream().map(Contract::getRoomId).filter(java.util.Objects::nonNull)
        .distinct().toList();
    if (!roomIds.isEmpty()) {
      for (Room r : roomRepository.findAllById(roomIds)) {
        roomMap.put(r.getId(), r);
      }
    }

    StringBuilder message = new StringBuilder("找到以下合同：\n");
    for (Contract contract : contracts) {
      Room room = roomMap.get(contract.getRoomId());
      String roomNo = room == null ? "-" : room.getRoomNumber();
      message.append("- 合同编号：")
          .append(resolver.safe(contract.getContractNo()))
          .append("，房间").append(roomNo)
          .append("，").append(contract.getStartDate()).append("至").append(contract.getEndDate())
          .append("，状态：").append(resolver.displayContractStatus(contract.getStatus()))
          .append("\n");
    }

    AiAssistantResponse response = AiAssistantResponse.ok(message.toString().trim(), "QUERY_CONTRACT");
    response.setDetailLink("/contracts?q=" + tenantName);
    return response;
  }

  // ---------------- 查用电量 ----------------

  public AiAssistantResponse queryElectricity(Map<String, Object> entities) {
    String roomNumber = resolver.getText(entities, "room_number");
    if (!resolver.hasText(roomNumber)) {
      return AiAssistantResponse.fail("缺少房间号，请补充例如\"101房间\"。", "QUERY_ELECTRICITY");
    }

    AiEntityResolver.RoomMatch match = resolver.resolveRoom(roomNumber, resolver.getText(entities, "building_name"));
    if (match.isAmbiguous()) {
      AiAssistantResponse clarify = AiAssistantResponse.fail(match.message(), "QUERY_ELECTRICITY");
      clarify.setRequiresClarification(true);
      clarify.setSuggestions(match.suggestions());
      return clarify;
    }
    if (!match.isFound()) {
      return AiAssistantResponse.fail(match.message(), "QUERY_ELECTRICITY");
    }
    Room room = match.room();

    YearMonth month = resolver.parseMonthOrDefault(resolver.getText(entities, "month"), YearMonth.now());
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();

    // 优先用「月初之前最后一次」与「月末最后一次」做差；缺失时回退到区间内首尾
    MeterReading prev = meterReadingRepository
        .findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(room.getId(), start);
    MeterReading curr = meterReadingRepository
        .findTopByRoomIdAndRecordDateLessThanEqualOrderByRecordDateDesc(room.getId(), end);

    double usage = 0.0;
    if (prev != null && curr != null && prev.getElectricReading() != null && curr.getElectricReading() != null) {
      usage = Math.max(0.0, curr.getElectricReading() - prev.getElectricReading());
    } else {
      List<MeterReading> list = meterReadingRepository
          .findByRoomIdAndRecordDateBetweenOrderByRecordDateAsc(room.getId(), start, end);
      if (list.size() >= 2) {
        Double first = list.get(0).getElectricReading();
        Double last = list.get(list.size() - 1).getElectricReading();
        if (first != null && last != null) {
          usage = Math.max(0.0, last - first);
        }
      }
    }

    Building building = room.getBuildingId() == null ? null
        : buildingRepository.findById(room.getBuildingId()).orElse(null);
    String roomDisplay = (building == null ? "" : building.getName()) + room.getRoomNumber();

    AiAssistantResponse response = AiAssistantResponse.ok(
        roomDisplay + " 在 " + month + " 的用电量为 " + String.format(Locale.ROOT, "%.2f", usage) + " 度。",
        "QUERY_ELECTRICITY");
    response.setDetailLink("/bills?month=" + month + "&roomId=" + room.getId());
    return response;
  }

  // ---------------- 内部 ----------------

  /** 同名学生澄清：列出每个候选的房间与合同状态。 */
  private AiAssistantResponse buildTenantClarification(String intent, String tenantName,
      List<com.house.model.Tenant> tenants) {
    List<Map<String, Object>> suggestions = new ArrayList<>();
    for (com.house.model.Tenant tenant : tenants) {
      List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
      Contract active = contracts.stream()
          .filter(c -> ContractStatus.isActive(resolver.safe(c.getStatus())))
          .findFirst()
          .orElse(contracts.stream().findFirst().orElse(null));
      Room room = active == null || active.getRoomId() == null ? null
          : roomRepository.findById(active.getRoomId()).orElse(null);
      Building building = room == null || room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);

      Map<String, Object> item = new java.util.LinkedHashMap<>();
      item.put("tenantName", tenant.getName());
      item.put("buildingName", building == null ? "-" : building.getName());
      item.put("roomNumber", room == null ? "-" : room.getRoomNumber());
      item.put("contractStatus", active == null ? "未知" : resolver.displayContractStatus(active.getStatus()));
      suggestions.add(item);
    }

    StringBuilder text = new StringBuilder("找到多位\"").append(tenantName).append("\"，请指定具体房间号：\n");
    for (Map<String, Object> item : suggestions) {
      text.append("- ")
          .append(item.get("buildingName"))
          .append(item.get("roomNumber"))
          .append("（合同").append(item.get("contractStatus")).append("）\n");
    }
    text.append("请重新输入，例如\"查一下A栋101的").append(tenantName).append("合同\"。");

    AiAssistantResponse response = AiAssistantResponse.fail(text.toString().trim(), intent);
    response.setRequiresClarification(true);
    response.setSuggestions(suggestions);
    return response;
  }

  /** 逾期天数：以出账日次日为第 1 天，未出账视为 0。 */
  private int overdueDays(Bill bill) {
    if (bill.getGeneratedDate() == null) {
      return 0;
    }
    long days = ChronoUnit.DAYS.between(bill.getGeneratedDate().toLocalDate(), LocalDate.now()) - 1;
    return (int) Math.max(days, 0);
  }

  private record ResolvedMeterRecord(Room room, LocalDate date,
      Double waterReading, Double electricReading, String displayRoom) {
  }
}
