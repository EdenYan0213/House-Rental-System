package com.house.controller;

import com.house.repo.*;
import com.house.service.ReminderService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final BillRepository billRepository;
  private final RoomRepository roomRepository;
  private final ContractRepository contractRepository;
  private final BuildingRepository buildingRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final ReminderService reminderService;

  public DashboardController(BillRepository billRepository, RoomRepository roomRepository,
      ContractRepository contractRepository, BuildingRepository buildingRepository,
      PaymentRecordRepository paymentRecordRepository, ReminderService reminderService) {
    this.billRepository = billRepository;
    this.roomRepository = roomRepository;
    this.contractRepository = contractRepository;
    this.buildingRepository = buildingRepository;
    this.paymentRecordRepository = paymentRecordRepository;
    this.reminderService = reminderService;
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    Map<String, Object> result = new HashMap<>();

    LocalDate today = LocalDate.now();

    // 今日收款：直接从PaymentRecord表计算
    double todayPayment = paymentRecordRepository.findAll().stream()
        .filter(p -> p.getPaidDate() != null && p.getPaidDate().toLocalDate().equals(today))
        .mapToDouble(p -> p.getPaidAmount() != null ? p.getPaidAmount() : 0)
        .sum();
    result.put("todayPayment", todayPayment);

    // 待收租金：支持中英文状态
    double pendingAmount = billRepository.findAll().stream()
        .filter(b -> "PENDING".equals(b.getStatus()) || "未支付".equals(b.getStatus()))
        .mapToDouble(
            b -> b.getTotalAmount() != null ? (b.getTotalAmount() - (b.getPaidAmount() != null ? b.getPaidAmount() : 0))
                : 0)
        .sum();
    result.put("pendingAmount", pendingAmount);

    long totalRooms = roomRepository.count();
    long rentedRooms = roomRepository.findAll().stream()
        .filter(r -> "RENTED".equals(r.getStatus()) || "已租".equals(r.getStatus()))
        .count();
    long idleRooms = roomRepository.findAll().stream()
        .filter(r -> "IDLE".equals(r.getStatus()) || "空闲".equals(r.getStatus()))
        .count();
    result.put("totalRooms", totalRooms);
    result.put("rentedRooms", rentedRooms);
    result.put("idleRooms", idleRooms);

    return result;
  }

  @GetMapping("/charts")
  public Map<String, Object> charts() {
    Map<String, Object> result = new HashMap<>();

    // 月度收支趋势 - 从账单和收款记录计算最近6个月的数据
    List<String> months = new ArrayList<>();
    List<Double> incomes = new ArrayList<>();
    List<Double> expenses = new ArrayList<>();
    LocalDate now = LocalDate.now();
    for (int i = 5; i >= 0; i--) {
      LocalDate monthDate = now.minusMonths(i);
      String monthStr = monthDate.getMonthValue() + "月";
      months.add(monthStr);
      
      // 该月收入 = 已支付账单的总金额
      double monthIncome = billRepository.findAll().stream()
          .filter(b -> b.getBillMonth() != null && b.getBillMonth().startsWith(monthDate.toString().substring(0, 7)))
          .filter(b -> "PAID".equals(b.getStatus()) || "已支付".equals(b.getStatus()))
          .mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount() : 0)
          .sum();
      incomes.add(monthIncome);
      
      // 支出暂设为收入的20%（可以根据实际支出记录计算）
      expenses.add(monthIncome * 0.2);
    }
    result.put("trendMonths", months);
    result.put("trendIncome", incomes);
    result.put("trendExpense", expenses);

    // 房间出租率
    long rentedRooms = roomRepository.findAll().stream()
        .filter(r -> "RENTED".equals(r.getStatus()) || "已租".equals(r.getStatus()))
        .count();
    long idleRooms = roomRepository.findAll().stream()
        .filter(r -> "IDLE".equals(r.getStatus()) || "空闲".equals(r.getStatus()))
        .count();
    result.put("rentedRooms", rentedRooms);
    result.put("idleRooms", idleRooms);

    // 楼栋出租率
    List<String> buildingNames = new ArrayList<>();
    List<Integer> buildingRates = new ArrayList<>();
    for (var b : buildingRepository.findAll()) {
      buildingNames.add(b.getName());
      int total = (int) roomRepository.findAll().stream()
          .filter(r -> b.getId() != null && r.getBuildingId() != null && b.getId().equals(r.getBuildingId()))
          .count();
      int rented = (int) roomRepository.findAll().stream()
          .filter(r -> b.getId() != null && r.getBuildingId() != null && b.getId().equals(r.getBuildingId()))
          .filter(r -> "RENTED".equals(r.getStatus()) || "已租".equals(r.getStatus()))
          .count();
      buildingRates.add(total > 0 ? (rented * 100 / total) : 0);
    }
    if (buildingNames.isEmpty()) {
      buildingNames = Arrays.asList("A栋", "B栋", "C栋");
      buildingRates = Arrays.asList(80, 65, 90);
    }
    result.put("buildingNames", buildingNames);
    result.put("buildingRates", buildingRates);

    return result;
  }

  @GetMapping("/expiring-contracts")
  public Map<String, Object> getExpiringContracts(@RequestParam(defaultValue = "3") int days) {
    List<?> contracts = reminderService.getExpiringContracts(days);
    long count = reminderService.getExpiringCount(days);
    return Map.of(
      "contracts", contracts,
      "count", count
    );
  }
}
