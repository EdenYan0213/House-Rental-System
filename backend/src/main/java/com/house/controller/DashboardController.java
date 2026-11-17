package com.house.controller;

import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.*;
import com.house.service.ReminderService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
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

    // 待收租金：未付清且未作废的账单，取剩余未收金额
    List<Bill> allBills = billRepository.findAll();
    double pendingAmount = allBills.stream()
        .filter(b -> BillStatus.isOutstanding(b.getStatus()))
        .mapToDouble(b -> {
          double total = b.getTotalAmount() != null ? b.getTotalAmount() : 0;
          double paid = b.getPaidAmount() != null ? b.getPaidAmount() : 0;
          return Math.max(total - paid, 0);
        })
        .sum();
    result.put("pendingAmount", pendingAmount);

    result.put("overdueCount", allBills.stream()
        .filter(b -> BillStatus.isOutstanding(b.getStatus()))
        .count());

    List<Room> allRooms = roomRepository.findAll();
    long totalRooms = allRooms.size();
    long rentedRooms = allRooms.stream()
        .filter(r -> RoomStatus.isRented(r.getStatus()))
        .count();
    long idleRooms = allRooms.stream()
        .filter(r -> RoomStatus.isIdle(r.getStatus()))
        .count();
    result.put("totalRooms", totalRooms);
    result.put("rentedRooms", rentedRooms);
    result.put("idleRooms", idleRooms);

    return result;
  }

  @GetMapping("/charts")
  public Map<String, Object> charts() {
    Map<String, Object> result = new HashMap<>();

    // 一次读取，避免在循环内重复 findAll
    List<Bill> allBills = billRepository.findAll();
    List<Room> allRooms = roomRepository.findAll();

    // 月度收支趋势 - 从账单和收款记录计算最近6个月的数据
    List<String> months = new ArrayList<>();
    List<Double> incomes = new ArrayList<>();
    List<Double> expenses = new ArrayList<>();
    LocalDate now = LocalDate.now();
    for (int i = 5; i >= 0; i--) {
      YearMonth monthDate = YearMonth.from(now).minusMonths(i);
      String monthKey = monthDate.toString(); // yyyy-MM
      months.add(monthDate.getMonthValue() + "月");

      // 该月收入 = 已付清账单的总金额
      double monthIncome = allBills.stream()
          .filter(b -> b.getBillMonth() != null && b.getBillMonth().startsWith(monthKey))
          .filter(b -> BillStatus.isPaid(b.getStatus()))
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
    result.put("rentedRooms", allRooms.stream().filter(r -> RoomStatus.isRented(r.getStatus())).count());
    result.put("idleRooms", allRooms.stream().filter(r -> RoomStatus.isIdle(r.getStatus())).count());

    // 楼栋出租率：无楼栋时返回空列表，由前端展示空态（不再回填示例数据）
    List<String> buildingNames = new ArrayList<>();
    List<Integer> buildingRates = new ArrayList<>();
    for (var b : buildingRepository.findAll()) {
      if (b.getName() == null) {
        continue;
      }
      long total = allRooms.stream()
          .filter(r -> b.getId() != null && b.getId().equals(r.getBuildingId()))
          .count();
      long rented = allRooms.stream()
          .filter(r -> b.getId() != null && b.getId().equals(r.getBuildingId()))
          .filter(r -> RoomStatus.isRented(r.getStatus()))
          .count();
      buildingNames.add(b.getName());
      buildingRates.add(total > 0 ? (int) (rented * 100 / total) : 0);
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
