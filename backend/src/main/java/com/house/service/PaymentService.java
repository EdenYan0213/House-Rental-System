package com.house.service;

import com.house.dto.PaymentRequest;
import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.PaymentRecord;
import com.house.repo.BillRepository;
import com.house.repo.PaymentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {
  private final PaymentRecordRepository paymentRecordRepository;
  private final BillRepository billRepository;

  public PaymentService(PaymentRecordRepository paymentRecordRepository, BillRepository billRepository) {
    this.paymentRecordRepository = paymentRecordRepository;
    this.billRepository = billRepository;
  }

  @Transactional
  public PaymentRecord recordPayment(Long billId, PaymentRequest req) {
    Bill bill = billRepository.findById(billId).orElseThrow(() -> new IllegalArgumentException("账单不存在"));
    if (BillStatus.isPaid(bill.getStatus()))
      throw new IllegalStateException("账单已付清，不能重复收款");
    if (BillStatus.isVoid(bill.getStatus()))
      throw new IllegalStateException("账单已作废，不能收款");

    double amount = req.getPaidAmount() == null ? 0.0 : req.getPaidAmount();
    if (amount <= 0)
      throw new IllegalArgumentException("支付金额需大于0");

    LocalDateTime paidDate = req.getPaidDate() == null ? LocalDateTime.now() : req.getPaidDate();

    PaymentRecord rec = PaymentRecord.builder()
        .billId(billId)
        .paidAmount(amount)
        .paidDate(paidDate)
        .paymentMethod(req.getPaymentMethod())
        .remark(req.getRemark())
        .build();
    PaymentRecord saved = paymentRecordRepository.save(rec);

    // 计算已付金额并更新账单状态
    List<PaymentRecord> payments = paymentRecordRepository.findByBillId(billId);
    double paidTotal = payments.stream().mapToDouble(p -> p.getPaidAmount() == null ? 0.0 : p.getPaidAmount()).sum();

    if (paidTotal >= (bill.getTotalAmount() == null ? 0.0 : bill.getTotalAmount())) {
      bill.setStatus(BillStatus.PAID);
    } else {
      bill.setStatus(BillStatus.PARTIAL);
    }
    billRepository.save(bill);

    return saved;
  }

  /**
   * 批量收款：对多个账单按「应收剩余金额」全额收款。
   *
   * <p>逐条处理并收集结果，单条失败不影响其它账单；整体仍在一个事务里，
   * 由调用方决定是否需要全部成功。返回成功/失败明细供前端展示。
   */
  @Transactional
  public Map<String, Object> recordBatchPayment(List<Long> billIds, String paymentMethod,
      LocalDateTime paidDate, String remark) {
    if (billIds == null || billIds.isEmpty()) {
      throw new IllegalArgumentException("请选择要收款的账单");
    }

    int successCount = 0;
    double totalPaid = 0;
    List<Map<String, Object>> failures = new ArrayList<>();

    for (Long billId : billIds) {
      try {
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> new IllegalArgumentException("账单不存在"));
        if (BillStatus.isPaid(bill.getStatus())) {
          throw new IllegalStateException("已付清");
        }
        if (BillStatus.isVoid(bill.getStatus())) {
          throw new IllegalStateException("已作废");
        }

        double total = bill.getTotalAmount() == null ? 0 : bill.getTotalAmount();
        double paid = paymentRecordRepository.findByBillId(billId).stream()
            .mapToDouble(p -> p.getPaidAmount() == null ? 0 : p.getPaidAmount())
            .sum();
        double remain = Math.max(total - paid, 0);
        if (remain <= 0) {
          throw new IllegalStateException("无可收金额");
        }

        PaymentRecord rec = PaymentRecord.builder()
            .billId(billId)
            .paidAmount(remain)
            .paidDate(paidDate == null ? LocalDateTime.now() : paidDate)
            .paymentMethod(paymentMethod)
            .remark(remark)
            .build();
        paymentRecordRepository.save(rec);

        bill.setStatus(BillStatus.PAID);
        billRepository.save(bill);

        successCount++;
        totalPaid += remain;
      } catch (Exception e) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("billId", billId);
        failure.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        failures.add(failure);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failCount", failures.size());
    result.put("totalPaid", totalPaid);
    result.put("failures", failures);
    return result;
  }
}
