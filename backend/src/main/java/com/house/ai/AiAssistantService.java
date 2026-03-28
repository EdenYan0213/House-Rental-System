package com.house.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.Bill;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.MeterReading;
import com.house.model.RemindLog;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.MeterReadingRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import com.house.service.MeterReadingBatchService;
import com.house.service.SmsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiAssistantService {

  private final AiAssistantProperties properties;
  private final ObjectMapper objectMapper;
  private final MeterReadingRepository meterReadingRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final MeterReadingBatchService meterReadingBatchService;
  private final SmsService smsService;

  public AiAssistantService(
      AiAssistantProperties properties,
      ObjectMapper objectMapper,
      MeterReadingRepository meterReadingRepository,
      RoomRepository roomRepository,
      BuildingRepository buildingRepository,
      TenantRepository tenantRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      MeterReadingBatchService meterReadingBatchService,
      SmsService smsService) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.meterReadingRepository = meterReadingRepository;
    this.roomRepository = roomRepository;
    this.buildingRepository = buildingRepository;
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.meterReadingBatchService = meterReadingBatchService;
    this.smsService = smsService;
  }

  public AiExecutionOutcome execute(String userInput) {
    List<AiAssistantProperties.ModelProvider> providers = resolveOrderedProviders();
    if (providers.isEmpty()) {
      throw new RuntimeException("未配置可用大模型");
    }

    int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
    maxAttempts = Math.min(maxAttempts, providers.size());

    List<String> errors = new ArrayList<>();
    for (int i = 0; i < maxAttempts; i++) {
      AiAssistantProperties.ModelProvider provider = providers.get(i);
      try {
        InvocationState.clear();
        String modelReply = createChatClient(provider).prompt()
            .system(buildToolSystemPrompt())
            .user(userInput)
            .tools(this)
            .call()
            .content();

        InvocationState state = InvocationState.get();
        AiAssistantResponse response;
        String intent;
        Map<String, Object> entities;
        if (state.response != null) {
          response = state.response;
          intent = state.intent;
          entities = state.entities;
        } else {
          response = AiAssistantResponse.fail(hasText(modelReply) ? modelReply : properties.getFallbackMessage(),
              "UNKNOWN");
          intent = "UNKNOWN";
          entities = Map.of();
        }

        response.setIntent(intent);
        response.setModel(provider.getName());
        return new AiExecutionOutcome(response, intent, toJson(entities), provider.getName());
      } catch (Exception ex) {
        errors.add((provider.getName() == null ? "unknown" : provider.getName()) + ": " + ex.getMessage());
      } finally {
        InvocationState.clear();
      }
    }

    throw new RuntimeException("所有模型调用失败: " + String.join(" | ", errors));
  }

  @Tool(description = "录入房间水电读数。参数: roomNumber, waterReading, electricReading, 可选date(YYYY-MM-DD/今天/昨天/明天), 可选buildingName")
  public String toolMeterReading(String roomNumber, Double waterReading, Double electricReading, String date,
      String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("room_number", roomNumber);
    entities.put("water_reading", waterReading);
    entities.put("electric_reading", electricReading);
    entities.put("date", date);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handleMeterReading(entities);
    InvocationState.set("METER_READING", entities, response);
    return response.getMessage();
  }

  @Tool(description = "批量录入房间水电读数。参数: records数组(每项包含roomNumber, waterReading, electricReading, 可选date, 可选buildingName)，可选全局date和buildingName")
  public String toolMeterReadingBatch(List<Map<String, Object>> records, String date, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("records", records);
    entities.put("date", date);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handleMeterReadingBatch(entities);
    InvocationState.set("METER_READING", entities, response);
    return response.getMessage();
  }

  @Tool(description = "给租户发送账单催缴短信。参数: tenantName, 可选roomNumber, 可选buildingName")
  public String toolSendSms(String tenantName, String roomNumber, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("tenant_name", tenantName);
    entities.put("room_number", roomNumber);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handleSendSms(entities);
    InvocationState.set("SEND_SMS", entities, response);
    return response.getMessage();
  }

  @Tool(description = "查询租户合同信息。参数: tenantName")
  public String toolQueryContract(String tenantName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("tenant_name", tenantName);
    AiAssistantResponse response = handleQueryContract(entities);
    InvocationState.set("QUERY_CONTRACT", entities, response);
    return response.getMessage();
  }

  @Tool(description = "查询房间月用电量。参数: roomNumber, 可选month(YYYY-MM/上月/本月), 可选buildingName")
  public String toolQueryElectricity(String roomNumber, String month, String buildingName) {
    Map<String, Object> entities = new HashMap<>();
    entities.put("room_number", roomNumber);
    entities.put("month", month);
    entities.put("building_name", buildingName);
    AiAssistantResponse response = handleQueryElectricity(entities);
    InvocationState.set("QUERY_ELECTRICITY", entities, response);
    return response.getMessage();
  }

  private ChatClient createChatClient(AiAssistantProperties.ModelProvider provider) {
    EndpointParts endpoint = resolveEndpoint(provider.getBaseUrl());
    OpenAiApi openAiApi = OpenAiApi.builder()
        .baseUrl(endpoint.baseUrl)
        .completionsPath(endpoint.completionsPath)
        .apiKey(provider.getApiKey())
        .build();

    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(provider.getModel())
        .temperature(provider.getTemperature() == null ? 0.0 : provider.getTemperature())
        .build();

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .openAiApi(openAiApi)
        .defaultOptions(options)
        .build();
    return ChatClient.builder(chatModel).build();
  }

  private List<AiAssistantProperties.ModelProvider> resolveOrderedProviders() {
    List<AiAssistantProperties.ModelProvider> configured = properties.getProviders() == null
        ? List.of()
        : properties.getProviders();

    List<AiAssistantProperties.ModelProvider> enabled = configured.stream()
        .filter(Objects::nonNull)
        .filter(p -> hasText(p.getName()) && hasText(p.getBaseUrl()) && hasText(p.getApiKey()) && hasText(p.getModel()))
        .toList();

    if (!hasText(properties.getDefaultModel())) {
      return new ArrayList<>(enabled);
    }

    List<AiAssistantProperties.ModelProvider> ordered = new ArrayList<>();
    AiAssistantProperties.ModelProvider defaultProvider = enabled.stream()
        .filter(p -> properties.getDefaultModel().equalsIgnoreCase(p.getName()))
        .findFirst()
        .orElse(null);
    if (defaultProvider != null) {
      ordered.add(defaultProvider);
    }
    enabled.stream().filter(p -> defaultProvider == null || !p.getName().equalsIgnoreCase(defaultProvider.getName()))
        .forEach(ordered::add);
    return ordered;
  }

  private String normalizeBaseUrl(String baseUrl) {
    String normalized = baseUrl.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.endsWith("/chat/completions")) {
      normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
    }
    return normalized;
  }

  private EndpointParts resolveEndpoint(String rawBaseUrl) {
    String normalized = normalizeBaseUrl(rawBaseUrl);
    if (normalized.endsWith("/v4")) {
      return new EndpointParts(normalized.substring(0, normalized.length() - 3), "/v4/chat/completions");
    }
    if (normalized.endsWith("/v1")) {
      return new EndpointParts(normalized.substring(0, normalized.length() - 3), "/v1/chat/completions");
    }
    return new EndpointParts(normalized, "/v1/chat/completions");
  }

  private String buildToolSystemPrompt() {
    return "你是房屋租赁系统助手。你只能通过已提供的工具来完成任务。" +
        "如果用户要做抄表、发账单短信、查合同、查用电量，必须调用对应工具。" +
        "如果一句话中包含多个房间的抄表，优先调用批量抄表工具。" +
        "如果缺参数，也要先调用工具，由工具返回补充提示。" +
        "如果用户请求不在这4类中，直接简短回复无法处理，不要编造数据。";
  }

  private AiAssistantResponse handleMeterReading(Map<String, Object> entities) {
    String roomNumber = getText(entities, "room_number");
    Double waterReading = getDouble(entities, "water_reading");
    Double electricReading = getDouble(entities, "electric_reading");
    if (!hasText(roomNumber)) {
      return AiAssistantResponse.fail("缺少房间号，请补充例如\"101房间\"。", "METER_READING");
    }
    if (waterReading == null) {
      return AiAssistantResponse.fail("缺少水表读数，请补充水表数值。", "METER_READING");
    }
    if (electricReading == null) {
      return AiAssistantResponse.fail("缺少电表读数，请补充电表数值。", "METER_READING");
    }

    List<Map<String, Object>> records = new ArrayList<>();
    Map<String, Object> row = new HashMap<>();
    row.put("roomNumber", roomNumber);
    row.put("waterReading", waterReading);
    row.put("electricReading", electricReading);
    row.put("date", getText(entities, "date"));
    row.put("buildingName", getText(entities, "building_name"));
    records.add(row);

    Map<String, Object> batchEntities = new HashMap<>();
    batchEntities.put("records", records);
    batchEntities.put("date", getText(entities, "date"));
    batchEntities.put("building_name", getText(entities, "building_name"));
    return handleMeterReadingBatch(batchEntities);
  }

  private AiAssistantResponse handleMeterReadingBatch(Map<String, Object> entities) {
    List<Map<String, Object>> records = toMapList(entities.get("records"));
    if (records.isEmpty()) {
      return AiAssistantResponse.fail("缺少抄表数据，请按“房间号+水表+电表”提供至少1条记录。", "METER_READING");
    }

    LocalDate defaultDate = parseDateOrDefault(getText(entities, "date"), LocalDate.now());
    String defaultBuildingName = getText(entities, "building_name");

    List<ResolvedMeterRecord> resolved = new ArrayList<>();
    for (int i = 0; i < records.size(); i++) {
      Map<String, Object> row = records.get(i);
      int rowNo = i + 1;

      String roomNumber = getTextAny(row, "roomNumber", "room_number");
      if (!hasText(roomNumber)) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少房间号，请补充。", "METER_READING");
      }

      Double waterReading = getDoubleAny(row, "waterReading", "water_reading");
      if (waterReading == null) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少水表读数，请补充。", "METER_READING");
      }

      Double electricReading = getDoubleAny(row, "electricReading", "electric_reading");
      if (electricReading == null) {
        return AiAssistantResponse.fail("第" + rowNo + "条缺少电表读数，请补充。", "METER_READING");
      }

      if (waterReading < 0 || electricReading < 0) {
        return AiAssistantResponse.fail("第" + rowNo + "条读数不能为负数。", "METER_READING");
      }

      String rowDateText = getTextAny(row, "date", "recordDate", "record_date");
      LocalDate rowDate = parseDateOrDefault(rowDateText, defaultDate);
      String rowBuilding = getTextAny(row, "buildingName", "building_name");
      if (!hasText(rowBuilding)) {
        rowBuilding = defaultBuildingName;
      }

      RoomMatchResult roomMatch = resolveRoom(roomNumber, rowBuilding);
      if (roomMatch.getResponse() != null) {
        return roomMatch.getResponse();
      }

      Room room = roomMatch.getRoom();
      Building building = room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);
      String displayRoom = (building == null ? "" : building.getName()) + room.getRoomNumber();

      resolved.add(new ResolvedMeterRecord(room, rowDate, waterReading, electricReading, displayRoom));
    }

    try {
      List<MeterReadingBatchService.MeterUpsertItem> items = resolved.stream()
          .map(r -> new MeterReadingBatchService.MeterUpsertItem(
              r.room.getId(),
              r.recordDate,
              r.waterReading,
              r.electricReading))
          .toList();
      meterReadingBatchService.upsertBatch(items);
    } catch (IllegalArgumentException ex) {
      return AiAssistantResponse.fail(ex.getMessage(), "METER_READING");
    }

    if (resolved.size() == 1) {
      ResolvedMeterRecord one = resolved.get(0);
      AiAssistantResponse response = AiAssistantResponse.ok(
          "房间 " + one.displayRoom + " 水电读数录入成功（水：" + one.waterReading + "，电：" + one.electricReading + "）",
          "METER_READING");
      response.setDetailLink("/meter-readings?date=" + one.recordDate + "&buildingId=" + one.room.getBuildingId());
      return response;
    }

    String sample = resolved.stream()
        .limit(3)
        .map(r -> r.displayRoom)
        .collect(Collectors.joining("、"));
    AiAssistantResponse response = AiAssistantResponse.ok(
        "已批量录入" + resolved.size() + "条水电读数（示例：" + sample + "）。",
        "METER_READING");

    Set<LocalDate> dates = resolved.stream().map(r -> r.recordDate).collect(Collectors.toSet());
    Set<Long> buildingIds = resolved.stream().map(r -> r.room.getBuildingId()).collect(Collectors.toSet());
    if (dates.size() == 1 && buildingIds.size() == 1) {
      response.setDetailLink(
          "/meter-readings?date=" + resolved.get(0).recordDate + "&buildingId=" + resolved.get(0).room.getBuildingId());
    } else {
      response.setDetailLink("/meter-readings");
    }
    return response;
  }

  private AiAssistantResponse handleSendSms(Map<String, Object> entities) {
    String tenantName = getText(entities, "tenant_name");
    if (!hasText(tenantName)) {
      return AiAssistantResponse.fail("缺少租户姓名，请补充后重试。", "SEND_SMS");
    }

    TenantSelection selection = resolveTenantForAction(tenantName, entities, true);
    if (selection.getResponse() != null) {
      return selection.getResponse();
    }

    TenantContractContext ctx = selection.getContext();
    if (ctx == null || ctx.contract == null) {
      return AiAssistantResponse.fail("未找到进行中的合同，无法发送催缴短信。", "SEND_SMS");
    }

    List<Bill> overdueBills = billRepository.findByContractId(ctx.contract.getId()).stream()
        .filter(this::isPendingStatus)
        .filter(b -> overdueDays(b) > 0)
        .toList();

    if (overdueBills.isEmpty()) {
      return AiAssistantResponse.ok("该租户暂无逾期账单。", "SEND_SMS");
    }

    if (!hasText(ctx.tenant.getPhone())) {
      return AiAssistantResponse.fail("该租户没有手机号，无法发送短信。", "SEND_SMS");
    }

    int maxOverdueDays = overdueBills.stream().mapToInt(this::overdueDays).max().orElse(0);
    double total = overdueBills.stream().mapToDouble(b -> b.getTotalAmount() == null ? 0 : b.getTotalAmount()).sum();
    String template = properties.getSmsOverdueTemplate();
    String content = template
        .replace("{租户姓名}", safe(ctx.tenant.getName()))
        .replace("{房间号}", safe(ctx.room == null ? null : ctx.room.getRoomNumber()))
        .replace("{逾期笔数}", String.valueOf(overdueBills.size()))
        .replace("{总欠费金额}", String.format(Locale.ROOT, "%.2f", total))
        .replace("{逾期天数}", String.valueOf(maxOverdueDays));

    RemindLog log = RemindLog.builder()
        .tenantId(ctx.tenant.getId())
        .tenantName(ctx.tenant.getName())
        .phone(ctx.tenant.getPhone())
        .roomNumber(ctx.room == null ? null : ctx.room.getRoomNumber())
        .overdueDays(maxOverdueDays)
        .billIds(overdueBills.stream().map(Bill::getId).collect(Collectors.toList()).toString())
        .totalAmount(total)
        .content(content)
        .build();

    RemindLog sent = smsService.sendSms(log);
    boolean success = "success".equalsIgnoreCase(sent.getStatus());
    String message = success ? "账单短信已发送给" + ctx.tenant.getName() : "短信发送失败：" + safe(sent.getFailReason());

    AiAssistantResponse response = success ? AiAssistantResponse.ok(message, "SEND_SMS")
        : AiAssistantResponse.fail(message, "SEND_SMS");
    response.setDetailLink("/bills?status=PENDING&roomId=" + (ctx.room == null ? "" : ctx.room.getId()));
    return response;
  }

  private AiAssistantResponse handleQueryContract(Map<String, Object> entities) {
    String tenantName = getText(entities, "tenant_name");
    if (!hasText(tenantName)) {
      return AiAssistantResponse.fail("缺少租户姓名，请补充后重试。", "QUERY_CONTRACT");
    }

    List<Tenant> tenants = resolveTenantCandidates(tenantName);
    if (tenants.isEmpty()) {
      return AiAssistantResponse.fail("未找到租户\"" + tenantName + "\"，请确认姓名是否正确。", "QUERY_CONTRACT");
    }

    if (tenants.size() > 1) {
      return buildTenantClarification("QUERY_CONTRACT", tenantName, tenants);
    }

    Tenant tenant = tenants.get(0);
    List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
    if (contracts.isEmpty()) {
      return AiAssistantResponse.ok("租户\"" + tenantName + "\"暂无合同记录。", "QUERY_CONTRACT");
    }

    contracts.sort(Comparator.comparing(Contract::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())));
    StringBuilder message = new StringBuilder("找到以下合同：\n");
    for (Contract contract : contracts) {
      Room room = contract.getRoomId() == null ? null : roomRepository.findById(contract.getRoomId()).orElse(null);
      String roomNo = room == null ? "-" : room.getRoomNumber();
      message.append("- 合同编号：")
          .append(safe(contract.getContractNo()))
          .append("，房间")
          .append(roomNo)
          .append("，")
          .append(contract.getStartDate())
          .append("至")
          .append(contract.getEndDate())
          .append("，状态：")
          .append(displayContractStatus(contract.getStatus()))
          .append("\n");
    }

    AiAssistantResponse response = AiAssistantResponse.ok(message.toString().trim(), "QUERY_CONTRACT");
    response.setDetailLink("/contracts?q=" + tenantName);
    return response;
  }

  private AiAssistantResponse handleQueryElectricity(Map<String, Object> entities) {
    String roomNumber = getText(entities, "room_number");
    if (!hasText(roomNumber)) {
      return AiAssistantResponse.fail("缺少房间号，请补充例如\"101房间\"。", "QUERY_ELECTRICITY");
    }

    RoomMatchResult roomMatch = resolveRoom(roomNumber, getText(entities, "building_name"));
    if (roomMatch.getResponse() != null) {
      return roomMatch.getResponse();
    }
    Room room = roomMatch.getRoom();

    YearMonth month = parseMonthOrDefault(getText(entities, "month"), YearMonth.now());
    LocalDate start = month.atDay(1);
    LocalDate end = month.atEndOfMonth();

    MeterReading prev = meterReadingRepository.findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(room.getId(),
        start);
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

  private TenantSelection resolveTenantForAction(String tenantName, Map<String, Object> entities, boolean activeOnly) {
    List<Tenant> tenants = resolveTenantCandidates(tenantName);
    if (tenants.isEmpty()) {
      return TenantSelection.withResponse(
          AiAssistantResponse.fail("未找到租户\"" + tenantName + "\"，请确认姓名是否正确。",
              activeOnly ? "SEND_SMS" : "QUERY_CONTRACT"));
    }

    String roomHint = getText(entities, "room_number");
    String buildingHint = getText(entities, "building_name");

    List<TenantContractContext> contexts = new ArrayList<>();
    for (Tenant tenant : tenants) {
      List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
      if (activeOnly) {
        contracts = contracts.stream()
            .filter(c -> "ACTIVE".equalsIgnoreCase(safe(c.getStatus())) || "进行中".equals(c.getStatus())).toList();
      }
      for (Contract contract : contracts) {
        Room room = contract.getRoomId() == null ? null : roomRepository.findById(contract.getRoomId()).orElse(null);
        Building building = room == null || room.getBuildingId() == null ? null
            : buildingRepository.findById(room.getBuildingId()).orElse(null);
        if (hasText(roomHint) && (room == null || !roomHint.equalsIgnoreCase(safe(room.getRoomNumber())))) {
          continue;
        }
        if (hasText(buildingHint) && (building == null || !safe(building.getName()).contains(buildingHint))) {
          continue;
        }
        contexts.add(new TenantContractContext(tenant, contract, room, building));
      }
    }

    if (contexts.isEmpty()) {
      return TenantSelection.withResponse(
          AiAssistantResponse.fail("未找到可匹配的进行中合同，请补充楼栋和房间号后重试。", activeOnly ? "SEND_SMS" : "QUERY_CONTRACT"));
    }

    if (contexts.size() > 1) {
      List<Map<String, Object>> suggestions = contexts.stream().map(c -> {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tenantName", c.tenant.getName());
        item.put("buildingName", c.building == null ? "-" : c.building.getName());
        item.put("roomNumber", c.room == null ? "-" : c.room.getRoomNumber());
        item.put("contractStatus", displayContractStatus(c.contract.getStatus()));
        return item;
      }).toList();

      StringBuilder text = new StringBuilder("找到多位\"").append(tenantName).append("\"，请指定具体房间号：\n");
      for (Map<String, Object> suggestion : suggestions) {
        text.append("- ")
            .append(suggestion.get("buildingName"))
            .append(suggestion.get("roomNumber"))
            .append("（合同")
            .append(suggestion.get("contractStatus"))
            .append("）\n");
      }
      text.append("请重新输入，例如\"给A栋101的").append(tenantName).append("发账单短信\"。");

      AiAssistantResponse response = AiAssistantResponse.fail(text.toString().trim(),
          activeOnly ? "SEND_SMS" : "QUERY_CONTRACT");
      response.setRequiresClarification(true);
      response.setSuggestions(suggestions);
      return TenantSelection.withResponse(response);
    }

    return TenantSelection.withContext(contexts.get(0));
  }

  private List<Tenant> resolveTenantCandidates(String tenantName) {
    List<Tenant> raw = tenantRepository.findByNameContainingOrPhoneContaining(tenantName, tenantName);
    List<Tenant> exact = raw.stream().filter(t -> tenantName.equalsIgnoreCase(safe(t.getName()))).toList();
    return exact.isEmpty() ? raw : exact;
  }

  private AiAssistantResponse buildTenantClarification(String intent, String tenantName, List<Tenant> tenants) {
    List<Map<String, Object>> suggestions = new ArrayList<>();
    for (Tenant tenant : tenants) {
      List<Contract> contracts = contractRepository.findByTenantId(tenant.getId());
      Contract active = contracts.stream()
          .filter(c -> "ACTIVE".equalsIgnoreCase(safe(c.getStatus())) || "进行中".equals(c.getStatus()))
          .findFirst()
          .orElse(contracts.stream().findFirst().orElse(null));
      Room room = active == null || active.getRoomId() == null ? null
          : roomRepository.findById(active.getRoomId()).orElse(null);
      Building building = room == null || room.getBuildingId() == null ? null
          : buildingRepository.findById(room.getBuildingId()).orElse(null);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("tenantName", tenant.getName());
      item.put("buildingName", building == null ? "-" : building.getName());
      item.put("roomNumber", room == null ? "-" : room.getRoomNumber());
      item.put("contractStatus", active == null ? "未知" : displayContractStatus(active.getStatus()));
      suggestions.add(item);
    }

    StringBuilder text = new StringBuilder("找到多位\"").append(tenantName).append("\"，请指定具体房间号：\n");
    for (Map<String, Object> item : suggestions) {
      text.append("- ")
          .append(item.get("buildingName"))
          .append(item.get("roomNumber"))
          .append("（合同")
          .append(item.get("contractStatus"))
          .append("）\n");
    }
    text.append("请重新输入，例如\"查一下A栋101的").append(tenantName).append("合同\"。");

    AiAssistantResponse response = AiAssistantResponse.fail(text.toString().trim(), intent);
    response.setRequiresClarification(true);
    response.setSuggestions(suggestions);
    return response;
  }

  private RoomMatchResult resolveRoom(String roomNumber, String buildingName) {
    List<Room> all = roomRepository.findAll();
    Map<Long, Building> buildingMap = buildingRepository.findAll().stream()
        .collect(Collectors.toMap(Building::getId, b -> b));

    ParsedRoomInput parsed = parseCombinedRoomInput(roomNumber, buildingName, buildingMap);
    String requestedRoomRaw = hasText(parsed.roomNumber) ? parsed.roomNumber : roomNumber;
    String requestedRoomNormalized = normalizeRoomToken(requestedRoomRaw);

    List<Room> matched = all.stream().filter(r -> {
      String roomRaw = safe(r.getRoomNumber());
      String roomNormalized = normalizeRoomToken(roomRaw);
      if (hasText(requestedRoomNormalized)) {
        return requestedRoomNormalized.equalsIgnoreCase(roomNormalized);
      }
      return requestedRoomRaw.equalsIgnoreCase(roomRaw);
    }).toList();

    if (hasText(parsed.buildingName)) {
      String requestedBuilding = parsed.buildingName.trim();
      matched = matched.stream().filter(r -> {
        Building b = buildingMap.get(r.getBuildingId());
        return b != null && safe(b.getName()).contains(requestedBuilding);
      }).toList();
    }

    if (matched.isEmpty()) {
      return RoomMatchResult.withResponse(AiAssistantResponse.fail("未找到房间\"" + roomNumber + "\"。", "UNKNOWN"));
    }

    if (matched.size() > 1) {
      List<Map<String, Object>> suggestions = matched.stream().map(room -> {
        Map<String, Object> s = new LinkedHashMap<>();
        Building b = buildingMap.get(room.getBuildingId());
        s.put("buildingName", b == null ? "-" : b.getName());
        s.put("roomNumber", room.getRoomNumber());
        s.put("roomId", room.getId());
        return s;
      }).toList();

      StringBuilder text = new StringBuilder("房间号\"").append(roomNumber).append("\"存在多个，请补充楼栋：\n");
      for (Map<String, Object> suggestion : suggestions) {
        text.append("- ").append(suggestion.get("buildingName")).append(suggestion.get("roomNumber")).append("\n");
      }
      text.append("请重新输入，例如\"A栋101房间...\"。");

      AiAssistantResponse response = AiAssistantResponse.fail(text.toString().trim(), "UNKNOWN");
      response.setRequiresClarification(true);
      response.setSuggestions(suggestions);
      return RoomMatchResult.withResponse(response);
    }

    return RoomMatchResult.withRoom(matched.get(0));
  }

  private ParsedRoomInput parseCombinedRoomInput(String roomNumber, String buildingName,
      Map<Long, Building> buildingMap) {
    String resolvedBuilding = hasText(buildingName) ? buildingName.trim() : null;
    String resolvedRoom = roomNumber;
    if (!hasText(roomNumber)) {
      return new ParsedRoomInput(resolvedRoom, resolvedBuilding);
    }

    String candidate = roomNumber.trim();
    if (!hasText(resolvedBuilding) && buildingMap != null && !buildingMap.isEmpty()) {
      List<Building> buildings = buildingMap.values().stream()
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing((Building b) -> safe(b.getName()).length()).reversed())
          .toList();

      for (Building b : buildings) {
        String name = safe(b.getName());
        if (!hasText(name)) {
          continue;
        }
        if (candidate.contains(name)) {
          resolvedBuilding = name;
          resolvedRoom = candidate.replace(name, "").trim();
          break;
        }
      }
    }

    String normalizedRoom = normalizeRoomToken(resolvedRoom);
    if (hasText(normalizedRoom)) {
      resolvedRoom = normalizedRoom;
    }

    return new ParsedRoomInput(resolvedRoom, resolvedBuilding);
  }

  private String normalizeRoomToken(String value) {
    if (!hasText(value)) {
      return value;
    }
    String normalized = value.trim()
        .replace("房间", "")
        .replace("房号", "")
        .replace("房", "")
        .replace("室", "")
        .replace("号", "")
        .replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .replace("　", "");
    return normalized;
  }

  private boolean isPendingStatus(Bill bill) {
    String status = safe(bill.getStatus()).toUpperCase(Locale.ROOT);
    return "PENDING".equals(status) || "PARTIAL".equals(status) || "待支付".equals(bill.getStatus())
        || "部分支付".equals(bill.getStatus());
  }

  private int overdueDays(Bill bill) {
    if (bill.getGeneratedDate() == null) {
      return 0;
    }
    long days = java.time.temporal.ChronoUnit.DAYS.between(bill.getGeneratedDate().toLocalDate(), LocalDate.now()) - 1;
    return (int) Math.max(days, 0);
  }

  private LocalDate parseDateOrDefault(String text, LocalDate fallback) {
    if (!hasText(text)) {
      return fallback;
    }
    String normalized = text.trim();
    LocalDate today = LocalDate.now();

    if ("今天".equals(normalized)) {
      return today;
    }
    if ("昨天".equals(normalized)) {
      return today.minusDays(1);
    }
    if ("明天".equals(normalized)) {
      return today.plusDays(1);
    }
    if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return LocalDate.parse(normalized);
    }
    if (normalized.matches("\\d{1,2}月\\d{1,2}日")) {
      String[] parts = normalized.replace("日", "").split("月");
      int month = Integer.parseInt(parts[0]);
      int day = Integer.parseInt(parts[1]);
      return LocalDate.of(today.getYear(), month, day);
    }
    return fallback;
  }

  private YearMonth parseMonthOrDefault(String text, YearMonth fallback) {
    if (!hasText(text)) {
      return fallback;
    }
    String normalized = text.trim();
    if ("上个月".equals(normalized) || "上月".equals(normalized)) {
      return YearMonth.now().minusMonths(1);
    }
    if ("本月".equals(normalized) || "这个月".equals(normalized)) {
      return YearMonth.now();
    }
    if (normalized.matches("\\d{4}-\\d{2}")) {
      try {
        return YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM"));
      } catch (DateTimeParseException ignored) {
        return fallback;
      }
    }
    return fallback;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private String getText(Map<String, Object> entities, String key) {
    if (entities == null || !entities.containsKey(key) || entities.get(key) == null) {
      return null;
    }
    return String.valueOf(entities.get(key)).trim();
  }

  private String getTextAny(Map<String, Object> entities, String... keys) {
    if (entities == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      String value = getText(entities, key);
      if (hasText(value)) {
        return value;
      }
    }
    return null;
  }

  private Double getDouble(Map<String, Object> entities, String key) {
    if (entities == null || entities.get(key) == null) {
      return null;
    }
    Object val = entities.get(key);
    if (val instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(String.valueOf(val));
    } catch (Exception ex) {
      return null;
    }
  }

  private Double getDoubleAny(Map<String, Object> entities, String... keys) {
    if (entities == null || keys == null) {
      return null;
    }
    for (String key : keys) {
      Double value = getDouble(entities, key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> toMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            row.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        result.add(row);
      }
    }
    return result;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String displayContractStatus(String status) {
    String val = safe(status).toUpperCase(Locale.ROOT);
    if ("ACTIVE".equals(val) || "进行中".equals(status)) {
      return "进行中";
    }
    if ("PENDING".equals(val) || "待生效".equals(status)) {
      return "待生效";
    }
    if ("EXPIRED".equals(val) || "已到期".equals(status)) {
      return "已到期";
    }
    if ("TERMINATED".equals(val) || "已解约".equals(status)) {
      return "已解约";
    }
    return hasText(status) ? status : "未知";
  }

  private static class RoomMatchResult {
    private final Room room;
    private final AiAssistantResponse response;

    private RoomMatchResult(Room room, AiAssistantResponse response) {
      this.room = room;
      this.response = response;
    }

    static RoomMatchResult withRoom(Room room) {
      return new RoomMatchResult(room, null);
    }

    static RoomMatchResult withResponse(AiAssistantResponse response) {
      return new RoomMatchResult(null, response);
    }

    public Room getRoom() {
      return room;
    }

    public AiAssistantResponse getResponse() {
      return response;
    }
  }

  private static class ParsedRoomInput {
    private final String roomNumber;
    private final String buildingName;

    private ParsedRoomInput(String roomNumber, String buildingName) {
      this.roomNumber = roomNumber;
      this.buildingName = buildingName;
    }
  }

  private static class TenantSelection {
    private final TenantContractContext context;
    private final AiAssistantResponse response;

    private TenantSelection(TenantContractContext context, AiAssistantResponse response) {
      this.context = context;
      this.response = response;
    }

    static TenantSelection withContext(TenantContractContext context) {
      return new TenantSelection(context, null);
    }

    static TenantSelection withResponse(AiAssistantResponse response) {
      return new TenantSelection(null, response);
    }

    public TenantContractContext getContext() {
      return context;
    }

    public AiAssistantResponse getResponse() {
      return response;
    }
  }

  private static class TenantContractContext {
    private final Tenant tenant;
    private final Contract contract;
    private final Room room;
    private final Building building;

    private TenantContractContext(Tenant tenant, Contract contract, Room room, Building building) {
      this.tenant = tenant;
      this.contract = contract;
      this.room = room;
      this.building = building;
    }
  }

  private static class InvocationState {
    private static final ThreadLocal<InvocationState> HOLDER = ThreadLocal.withInitial(InvocationState::new);
    private String intent = "UNKNOWN";
    private Map<String, Object> entities = Map.of();
    private AiAssistantResponse response;

    static void set(String intent, Map<String, Object> entities, AiAssistantResponse response) {
      InvocationState state = HOLDER.get();
      state.intent = intent == null ? "UNKNOWN" : intent;
      state.entities = entities == null ? Map.of() : new LinkedHashMap<>(entities);
      state.response = response;
    }

    static InvocationState get() {
      return HOLDER.get();
    }

    static void clear() {
      HOLDER.remove();
    }
  }

  private static class EndpointParts {
    private final String baseUrl;
    private final String completionsPath;

    private EndpointParts(String baseUrl, String completionsPath) {
      this.baseUrl = baseUrl;
      this.completionsPath = completionsPath;
    }
  }

  private static class ResolvedMeterRecord {
    private final Room room;
    private final LocalDate recordDate;
    private final Double waterReading;
    private final Double electricReading;
    private final String displayRoom;

    private ResolvedMeterRecord(Room room, LocalDate recordDate, Double waterReading, Double electricReading,
        String displayRoom) {
      this.room = room;
      this.recordDate = recordDate;
      this.waterReading = waterReading;
      this.electricReading = electricReading;
      this.displayRoom = displayRoom;
    }
  }
}
