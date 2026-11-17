package com.house.service;

import com.house.dto.BillGenerationResult;
import com.house.model.*;
import com.house.repo.*;
import com.house.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Service
public class BillService {

  private static final Logger log = LoggerFactory.getLogger(BillService.class);

  private final ContractRepository contractRepository;
  private final MeterReadingRepository meterReadingRepository;
  private final FeeItemRepository feeItemRepository;
  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final RoomService roomService;
  private final TenantService tenantService;
  private final BuildingRepository buildingRepository;
  private final RoomRepository roomRepository;

  public BillService(ContractRepository contractRepository, MeterReadingRepository meterReadingRepository,
      FeeItemRepository feeItemRepository, BillRepository billRepository, BillDetailRepository billDetailRepository,
      RoomService roomService, TenantService tenantService, BuildingRepository buildingRepository,
      RoomRepository roomRepository) {
    this.contractRepository = contractRepository;
    this.meterReadingRepository = meterReadingRepository;
    this.feeItemRepository = feeItemRepository;
    this.billRepository = billRepository;
    this.billDetailRepository = billDetailRepository;
    this.roomService = roomService;
    this.tenantService = tenantService;
    this.buildingRepository = buildingRepository;
    this.roomRepository = roomRepository;
  }

  // 每月1号凌晨1点执行（示例）
  @Scheduled(cron = "0 0 1 1 * ?")
  @Transactional
  public void generateMonthlyBills() {
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    LocalDate start = lastMonth.atDay(1);
    LocalDate end = lastMonth.atEndOfMonth();
    String billMonth = lastMonth.toString(); // yyyy-MM

    List<Contract> active = contractRepository.findByStatus("ACTIVE");
    for (Contract c : active) {
      try {
        createBillForContract(c, start, end, billMonth);
      } catch (Exception e) {
        // TODO: log error per contract, continue
        e.printStackTrace();
      }
    }
  }

  // 手动触发指定月份的账单生成，month 格式: yyyy-MM
  @Transactional
  public int generateBillsForMonth(String month) {
    if (month == null || month.isBlank()) {
      YearMonth ym = YearMonth.now().minusMonths(1);
      month = ym.toString();
    }
    YearMonth ym = YearMonth.parse(month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    String billMonth = ym.toString();

    return generateBillsForMonthDetailed(month).successCount();
  }

  /**
   * 批量生成账单，返回结构化的成功/失败明细。
   *
   * <p>此前失败会整体抛异常，前端只能拿到一句拼接文本，无法定位是哪几个房间、
   * 也无法只重试失败项。现在逐个合同收集结果，交给前端展示失败清单并支持重试。
   */
  @Transactional
  public BillGenerationResult generateBillsForMonthDetailed(String month) {
    String targetMonth = month;
    if (targetMonth == null || targetMonth.isBlank()) {
      targetMonth = YearMonth.now().minusMonths(1).toString();
    }
    YearMonth ym = YearMonth.parse(targetMonth);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    String billMonth = ym.toString();

    List<Contract> active = contractRepository.findByStatus("ACTIVE");
    int successCount = 0;
    List<Map<String, Object>> failures = new ArrayList<>();

    for (Contract c : active) {
      try {
        createBillForContract(c, start, end, billMonth);
        successCount++;
      } catch (Exception e) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("contractId", c.getId());
        failure.put("contractNo", c.getContractNo());
        failure.put("roomId", c.getRoomId());
        failure.put("roomNumber", resolveRoomNumber(c.getRoomId()));
        failure.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        failures.add(failure);
        log.warn("账单生成失败 contractNo={} reason={}", c.getContractNo(), e.getMessage());
      }
    }

    return new BillGenerationResult(billMonth, successCount, active.size(), failures);
  }

  private String resolveRoomNumber(Long roomId) {
    if (roomId == null) {
      return null;
    }
    return roomRepository.findById(roomId).map(Room::getRoomNumber).orElse(null);
  }

  // 按合同ID生成账单
  @Transactional
  public int generateBillsForContract(Long contractId) {
    Optional<Contract> contractOpt = contractRepository.findById(contractId);
    if (contractOpt.isEmpty()) {
      throw new IllegalArgumentException("合同不存在");
    }
    Contract c = contractOpt.get();

    if (!"ACTIVE".equals(c.getStatus())) {
      throw new IllegalStateException("只有进行中的合同才能生成账单");
    }

    YearMonth ym = YearMonth.now();
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    String billMonth = ym.toString();

    try {
      createBillForContract(c, start, end, billMonth);
      return 1;
    } catch (Exception e) {
      throw new RuntimeException("账单生成失败: " + e.getMessage());
    }
  }

  // 按房间 + 指定月份生成账单（若 month 为空则默认上月）
  @Transactional
  public int generateBillsForRoomAndMonth(Long roomId, String month) {
    if (roomId == null) {
      throw new IllegalArgumentException("房间ID不能为空");
    }

    String targetMonth = month;
    if (targetMonth == null || targetMonth.isBlank()) {
      targetMonth = YearMonth.now().minusMonths(1).toString();
    }

    YearMonth ym = YearMonth.parse(targetMonth);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();

    List<Contract> contracts = contractRepository.findByRoomId(roomId);
    List<Contract> activeContracts = new ArrayList<>();
    for (Contract c : contracts) {
      String status = c.getStatus();
      if ("ACTIVE".equalsIgnoreCase(status) || "进行中".equals(status)) {
        activeContracts.add(c);
      }
    }

    if (activeContracts.isEmpty()) {
      throw new IllegalStateException("该房间无进行中的合同，无法生成账单");
    }

    LinkedHashMap<Long, Contract> unique = new LinkedHashMap<>();
    for (Contract c : activeContracts) {
      unique.put(c.getId(), c);
    }

    int count = 0;
    StringBuilder errors = new StringBuilder();
    for (Contract c : unique.values()) {
      try {
        createBillForContract(c, start, end, ym.toString());
        count++;
      } catch (Exception e) {
        errors.append("合同 ").append(c.getContractNo()).append(": ").append(e.getMessage()).append("; ");
      }
    }

    if (errors.length() > 0) {
      throw new RuntimeException("部分账单生成失败: " + errors);
    }
    return count;
  }

  // 按合同 + 指定月份生成/覆盖账单（未付款时会覆盖）
  @Transactional
  public void generateBillForContractAndMonth(Long contractId, String month) {
    if (month == null || month.isBlank()) {
      throw new IllegalArgumentException("月份不能为空");
    }
    Contract c = contractRepository.findById(contractId).orElseThrow(() -> new IllegalArgumentException("合同不存在"));
    if (!"ACTIVE".equals(c.getStatus())) {
      throw new IllegalStateException("只有进行中的合同才能生成账单");
    }
    YearMonth ym = YearMonth.parse(month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    createBillForContract(c, start, end, ym.toString());
  }

  public void createBillForContract(Contract c, LocalDate start, LocalDate end, String billMonth) {
    double baseRent = c.getMonthlyRent() == null ? 0.0 : c.getMonthlyRent();

    // 获取房间读数：使用 "本期最后一次读数 - 上期最后一次读数" 计算用量
    // 注意：很多场景会在月初(如 1-3 号)补录上月末抄表，因此对本期读数允许一个小的“跨月宽限期”
    double waterUsage = 0.0;
    double electricUsage = 0.0;
    Double prevWaterReading = null;
    Double currWaterReading = null;
    Double prevElectricReading = null;
    Double currElectricReading = null;
    if (c.getRoomId() != null) {
      // 上个月最后一次读数：recordDate < start 的最大值
      MeterReading prev = meterReadingRepository
          .findTopByRoomIdAndRecordDateLessThanOrderByRecordDateDesc(c.getRoomId(), start);
      // 本月（截至 end）最后一次读数：recordDate <= end 的最大值
      MeterReading curr = meterReadingRepository
          .findTopByRoomIdAndRecordDateLessThanEqualOrderByRecordDateDesc(c.getRoomId(), end);

      if (prev != null && curr != null) {
        if (prev.getWaterReading() != null && curr.getWaterReading() != null) {
          waterUsage = Math.max(0.0, curr.getWaterReading() - prev.getWaterReading());
        }
        if (prev.getElectricReading() != null && curr.getElectricReading() != null) {
          electricUsage = Math.max(0.0, curr.getElectricReading() - prev.getElectricReading());
        }
      } else if (curr != null) {
        // 如果没有上月读数，退回到月内首尾读数差（兼容旧数据）
        List<MeterReading> reads = meterReadingRepository
            .findByRoomIdAndRecordDateBetweenOrderByRecordDateAsc(c.getRoomId(), start, end);
        if (reads != null && reads.size() >= 1) {
          MeterReading first = reads.get(0);
          MeterReading last = reads.get(reads.size() - 1);
          if (first.getWaterReading() != null && last.getWaterReading() != null) {
            waterUsage = Math.max(0.0, last.getWaterReading() - first.getWaterReading());
          }
          if (first.getElectricReading() != null && last.getElectricReading() != null) {
            electricUsage = Math.max(0.0, last.getElectricReading() - first.getElectricReading());
          }
        }
      }
    }

    double waterUnit = feeItemRepository.findByName("水费").map(FeeItem::getUnitPrice).orElse(3.5);
    double electricUnit = feeItemRepository.findByName("电费").map(FeeItem::getUnitPrice).orElse(0.6);

    // 楼栋单价将在获取房间信息后优先覆盖（如果配置了）

    double waterAmount = waterUsage * waterUnit;
    double electricAmount = electricUsage * electricUnit;

    double total = baseRent + waterAmount + electricAmount;

    // 如果已存在该合同对应月份的账单，且未付款，则先删除旧账单（及其明细），以便重新生成；若已付款则拒绝覆盖
    billRepository.findByContractIdAndBillMonth(c.getId(), billMonth).ifPresent(existing -> {
      if ("PAID".equals(existing.getStatus())) {
        throw new IllegalStateException("已有已付款账单，不能重新生成");
      }
      List<BillDetail> old = billDetailRepository.findByBillId(existing.getId());
      if (old != null && !old.isEmpty()) {
        billDetailRepository.deleteAll(old);
        billDetailRepository.flush();
      }
      billRepository.deleteById(existing.getId());
      billRepository.flush();
    });

    Bill bill = new Bill();
    bill.setContractId(c.getId());
    bill.setBillMonth(billMonth);
    bill.setTotalAmount(total);
    bill.setStatus("PENDING");
    bill.setGeneratedDate(LocalDateTime.now());

    // 设置房间和租户信息
    if (c.getRoomId() != null) {
      Room room = roomService.findById(c.getRoomId()).orElse(null);
      if (room != null) {
        bill.setRoomId(room.getId());
        bill.setRoomNumber(room.getRoomNumber());
        if (room.getBuildingId() != null) {
          Building building = buildingRepository.findById(room.getBuildingId()).orElse(null);
          if (building != null) {
            bill.setBuildingName(building.getName());
          }
        }
      }
    }

    // 补充租户信息
    if (c.getTenantId() != null) {
      Tenant tenant = tenantService.findById(c.getTenantId()).orElse(null);
      if (tenant != null) {
        bill.setTenantName(tenant.getName());
        bill.setTenantPhone(tenant.getPhone());
      }
    }

    Bill saved = billRepository.save(bill);
    Long billId = saved.getId();

    // 查询房间信息
    Room room = roomService.findById(c.getRoomId()).orElse(null);

    // 如果房间所在楼栋有自定义单价，则优先使用楼栋单价
    if (room != null && room.getBuildingId() != null) {
      Building b = buildingRepository.findById(room.getBuildingId()).orElse(null);
      if (b != null) {
        if (b.getWaterUnitPrice() != null)
          waterUnit = b.getWaterUnitPrice();
        if (b.getElectricUnitPrice() != null)
          electricUnit = b.getElectricUnitPrice();
      }
    }

    // 保存明细：基础房费、水费、电费
    if (baseRent > 0) {
      BillDetail baseDetail = new BillDetail();
      baseDetail.setBillId(billId);
      baseDetail.setFeeItemId(null);
      baseDetail.setName("基础房费");
      baseDetail.setUsageAmount(1.0);
      baseDetail.setAmount(baseRent);
      billDetailRepository.save(baseDetail);
    }

    if (waterAmount > 0) {
      BillDetail waterDetail = new BillDetail();
      waterDetail.setBillId(billId);
      waterDetail.setFeeItemId(null);
      waterDetail.setName("水费");
      waterDetail.setUsageAmount(waterUsage);
      // 填充上期/本期读数（注意：BillDetail 的 prev/curr 为非持久化字段，仅用于接口返回展示）
      waterDetail.setPrevReading(prevWaterReading);
      waterDetail.setCurrReading(currWaterReading);
      waterDetail.setAmount(waterAmount);
      billDetailRepository.save(waterDetail);
    }

    if (electricAmount > 0) {
      BillDetail elecDetail = new BillDetail();
      elecDetail.setBillId(billId);
      elecDetail.setFeeItemId(null);
      elecDetail.setName("电费");
      elecDetail.setUsageAmount(electricUsage);
      elecDetail.setPrevReading(prevElectricReading);
      elecDetail.setCurrReading(currElectricReading);
      elecDetail.setAmount(electricAmount);
      billDetailRepository.save(elecDetail);
    }
  }
}
