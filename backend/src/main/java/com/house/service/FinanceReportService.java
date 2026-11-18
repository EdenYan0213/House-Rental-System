package com.house.service;

import com.house.dto.FinanceReportResult;
import com.house.model.Bill;
import com.house.model.BillDetail;
import com.house.model.BillStatus;
import com.house.model.Building;
import com.house.model.Contract;
import com.house.model.PaymentRecord;
import com.house.model.RepairOrder;
import com.house.model.Room;
import com.house.model.RoomStatus;
import com.house.repo.BillDetailRepository;
import com.house.repo.BillRepository;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.PaymentRecordRepository;
import com.house.repo.RepairOrderRepository;
import com.house.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 财务报表统计。
 *
 * <p>口径：应收/未收按账单（排除作废），实收按收款记录（按收款日期归属月份），
 * 支出按维修工单费用。
 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

  private final BillRepository billRepository;
  private final BillDetailRepository billDetailRepository;
  private final PaymentRecordRepository paymentRecordRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final ContractRepository contractRepository;
  private final RepairOrderRepository repairOrderRepository;

  /**
   * 生成财务报表。
   *
   * @param startMonth 起始月份 yyyy-MM，空则取 6 个月前
   * @param endMonth   结束月份 yyyy-MM，空则取当月
   */
  public FinanceReportResult build(String startMonth, String endMonth) {
    YearMonth end = parseMonth(endMonth, YearMonth.now());
    YearMonth start = parseMonth(startMonth, end.minusMonths(5));
    if (start.isAfter(end)) {
      YearMonth tmp = start;
      start = end;
      end = tmp;
    }

    List<String> months = new ArrayList<>();
    for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
      months.add(m.toString());
    }

    // 一次性加载，避免循环内查询
    List<Bill> allBills = billRepository.findAll();
    List<PaymentRecord> allPayments = paymentRecordRepository.findAll();
    List<Room> allRooms = roomRepository.findAll();
    Map<Long, Building> buildingById = buildingRepository.findAll().stream()
        .collect(Collectors.toMap(Building::getId, b -> b, (a, b) -> a));
    Map<Long, Contract> contractById = contractRepository.findAll().stream()
        .collect(Collectors.toMap(Contract::getId, c -> c, (a, b) -> a));

    // 区间内的账单（按 billMonth 过滤）与收款（按 paidDate 归属月）
    List<Bill> periodBills = allBills.stream()
        .filter(b -> b.getBillMonth() != null && months.contains(b.getBillMonth()))
        .toList();

    Map<Long, Double> billPaidAmount = new HashMap<>();
    for (PaymentRecord p : allPayments) {
      if (p.getBillId() == null) {
        continue;
      }
      billPaidAmount.merge(p.getBillId(), p.getPaidAmount() == null ? 0 : p.getPaidAmount(), Double::sum);
    }

    // ---- 总览 ----
    double receivable = 0;
    double receivedOnPeriodBills = 0;
    int billCount = 0;
    int paidBillCount = 0;
    for (Bill b : periodBills) {
      if (BillStatus.isVoid(b.getStatus())) {
        continue;
      }
      double total = nz(b.getTotalAmount());
      double paid = billPaidAmount.getOrDefault(b.getId(), 0.0);
      receivable += total;
      receivedOnPeriodBills += paid;
      billCount++;
      if (BillStatus.isFinal(b.getStatus())) {
        paidBillCount++;
      }
    }

    // 实收（真实现金流）：区间内的收款记录
    double cashReceived = allPayments.stream()
        .filter(p -> p.getPaidDate() != null)
        .filter(p -> {
          String m = YearMonth.from(p.getPaidDate()).toString();
          return months.contains(m);
        })
        .mapToDouble(p -> nz(p.getPaidAmount()))
        .sum();

    // 支出：区间内维修工单费用
    double expense = repairOrderRepository.findAll().stream()
        .filter(r -> r.getCreatedAt() != null)
        .filter(r -> months.contains(YearMonth.from(r.getCreatedAt()).toString()))
        .mapToDouble(r -> nz(r.getTotalFee()))
        .sum();

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("receivable", round(receivable));
    // 实收（现金流口径）：按收款日期归属月份，与「应收」的账单口径不同
    summary.put("received", round(cashReceived));
    // 账单口径的已收：仅统计这些账单自身的收款，用于计算未收
    summary.put("receivedOnBills", round(receivedOnPeriodBills));
    summary.put("unpaid", round(Math.max(receivable - receivedOnPeriodBills, 0)));
    summary.put("expense", round(expense));
    summary.put("netIncome", round(cashReceived - expense));
    summary.put("billCount", billCount);
    summary.put("paidBillCount", paidBillCount);
    summary.put("collectRate", receivable > 0
        ? Math.round(receivedOnPeriodBills / receivable * 100)
        : 0);

    // ---- 逐月趋势 ----
    List<Double> mReceivable = new ArrayList<>();
    List<Double> mReceived = new ArrayList<>();
    List<Double> mExpense = new ArrayList<>();
    for (String month : months) {
      double r = 0;
      double rc = 0;
      for (Bill b : periodBills) {
        if (!month.equals(b.getBillMonth()) || BillStatus.isVoid(b.getStatus())) {
          continue;
        }
        r += nz(b.getTotalAmount());
        rc += billPaidAmount.getOrDefault(b.getId(), 0.0);
      }
      double monthExpense = repairOrderRepository.findAll().stream()
          .filter(x -> x.getCreatedAt() != null)
          .filter(x -> month.equals(YearMonth.from(x.getCreatedAt()).toString()))
          .mapToDouble(x -> nz(x.getTotalFee()))
          .sum();
      mReceivable.add(round(r));
      mReceived.add(round(rc));
      mExpense.add(round(monthExpense));
    }

    Map<String, Object> trend = new LinkedHashMap<>();
    trend.put("months", months);
    trend.put("receivable", mReceivable);
    trend.put("received", mReceived);
    trend.put("expense", mExpense);

    // ---- 按楼栋 ----
    Map<String, List<Room>> roomsByBuilding = allRooms.stream()
        .filter(r -> r.getBuildingId() != null)
        .collect(Collectors.groupingBy(r -> {
          Building b = buildingById.get(r.getBuildingId());
          return b == null || b.getName() == null ? "未归属楼栋" : b.getName();
        }));

    Map<String, double[]> buildingAgg = new LinkedHashMap<>();
    Map<String, Integer> buildingRoomCount = new LinkedHashMap<>();
    for (Map.Entry<String, List<Room>> e : roomsByBuilding.entrySet()) {
      buildingAgg.put(e.getKey(), new double[]{0, 0, 0});
      buildingRoomCount.put(e.getKey(), e.getValue().size());
    }
    Map<Long, String> roomIdToBuilding = new HashMap<>();
    for (Map.Entry<String, List<Room>> e : roomsByBuilding.entrySet()) {
      for (Room r : e.getValue()) {
        roomIdToBuilding.put(r.getId(), e.getKey());
      }
    }
    for (Bill b : periodBills) {
      if (BillStatus.isVoid(b.getStatus()) || b.getRoomId() == null) {
        continue;
      }
      String bName = roomIdToBuilding.getOrDefault(b.getRoomId(), b.getBuildingName());
      if (bName == null) {
        continue;
      }
      double[] agg = buildingAgg.computeIfAbsent(bName, k -> new double[]{0, 0, 0});
      double total = nz(b.getTotalAmount());
      double paid = billPaidAmount.getOrDefault(b.getId(), 0.0);
      agg[0] += total;
      agg[1] += paid;
      agg[2] += Math.max(total - paid, 0);
    }

    List<Map<String, Object>> byBuilding = new ArrayList<>();
    for (Map.Entry<String, double[]> e : buildingAgg.entrySet()) {
      String name = e.getKey();
      double[] agg = e.getValue();
      List<Room> rooms = roomsByBuilding.getOrDefault(name, List.of());
      long rented = rooms.stream().filter(r -> RoomStatus.isRented(r.getStatus())).count();
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("buildingName", name);
      item.put("receivable", round(agg[0]));
      item.put("received", round(agg[1]));
      item.put("unpaid", round(agg[2]));
      item.put("roomCount", rooms.size());
      item.put("occupancyRate", rooms.isEmpty() ? 0 : Math.round(rented * 100.0 / rooms.size()));
      byBuilding.add(item);
    }
    byBuilding.sort((a, b) -> Double.compare(
        (Double) b.get("receivable"), (Double) a.get("receivable")));

    // ---- 按房间 ----
    Map<Long, double[]> roomAgg = new LinkedHashMap<>();
    Map<Long, Integer> roomBillCount = new LinkedHashMap<>();
    for (Bill b : periodBills) {
      if (BillStatus.isVoid(b.getStatus()) || b.getRoomId() == null) {
        continue;
      }
      double[] agg = roomAgg.computeIfAbsent(b.getRoomId(), k -> new double[]{0, 0, 0});
      double total = nz(b.getTotalAmount());
      double paid = billPaidAmount.getOrDefault(b.getId(), 0.0);
      agg[0] += total;
      agg[1] += paid;
      agg[2] += Math.max(total - paid, 0);
      roomBillCount.merge(b.getRoomId(), 1, Integer::sum);
    }

    Map<Long, Room> roomById = allRooms.stream()
        .collect(Collectors.toMap(Room::getId, r -> r, (a, b) -> a));

    List<Map<String, Object>> byRoom = new ArrayList<>();
    for (Map.Entry<Long, double[]> e : roomAgg.entrySet()) {
      Room room = roomById.get(e.getKey());
      if (room == null) {
        continue;
      }
      double[] agg = e.getValue();
      String bName = room.getBuildingId() == null ? null
          : buildingById.getOrDefault(room.getBuildingId(), new Building()).getName();
      Contract active = contractById.values().stream()
          .filter(c -> e.getKey().equals(c.getRoomId()) && "ACTIVE".equals(c.getStatus()))
          .findFirst().orElse(null);

      Map<String, Object> item = new LinkedHashMap<>();
      item.put("roomId", room.getId());
      item.put("roomNumber", room.getRoomNumber());
      item.put("buildingName", bName);
      item.put("tenantName", active == null ? null : active.getTenantName());
      item.put("receivable", round(agg[0]));
      item.put("received", round(agg[1]));
      item.put("unpaid", round(agg[2]));
      item.put("billCount", roomBillCount.getOrDefault(e.getKey(), 0));
      byRoom.add(item);
    }
    byRoom.sort((a, b) -> Double.compare(
        (Double) b.get("receivable"), (Double) a.get("receivable")));

    // ---- 费用构成 ----
    Map<String, Double> costMap = new LinkedHashMap<>();
    for (Bill b : periodBills) {
      if (BillStatus.isVoid(b.getStatus())) {
        continue;
      }
      for (BillDetail d : billDetailRepository.findByBillId(b.getId())) {
        String name = d.getName() == null ? "其他" : d.getName();
        costMap.merge(name, nz(d.getAmount()), Double::sum);
      }
    }
    List<Map<String, Object>> costBreakdown = costMap.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
        .map(e -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("name", e.getKey());
          m.put("amount", round(e.getValue()));
          return m;
        })
        .collect(Collectors.toList());

    // ---- 收款方式分布 ----
    Map<String, double[]> methodAgg = new LinkedHashMap<>();
    for (PaymentRecord p : allPayments) {
      if (p.getPaidDate() == null) {
        continue;
      }
      String m = YearMonth.from(p.getPaidDate()).toString();
      if (!months.contains(m)) {
        continue;
      }
      String method = p.getPaymentMethod() == null ? "未填写" : p.getPaymentMethod();
      double[] agg = methodAgg.computeIfAbsent(method, k -> new double[]{0, 0});
      agg[0] += nz(p.getPaidAmount());
      agg[1] += 1;
    }
    List<Map<String, Object>> paymentMethods = methodAgg.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
        .map(e -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("method", e.getKey());
          m.put("amount", round(e.getValue()[0]));
          m.put("count", (int) e.getValue()[1]);
          return m;
        })
        .collect(Collectors.toList());

    Map<String, Object> period = new LinkedHashMap<>();
    period.put("startMonth", start.toString());
    period.put("endMonth", end.toString());

    return new FinanceReportResult(period, summary, trend, byBuilding, byRoom,
        costBreakdown, paymentMethods);
  }

  private static YearMonth parseMonth(String value, YearMonth fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return YearMonth.parse(value.trim());
    } catch (Exception e) {
      return fallback;
    }
  }

  private static double nz(Double v) {
    return v == null ? 0 : v;
  }

  private static double round(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
