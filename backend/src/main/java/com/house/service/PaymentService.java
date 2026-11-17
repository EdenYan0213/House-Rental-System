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
import java.util.List;

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
}
