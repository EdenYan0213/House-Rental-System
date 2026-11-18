package com.house.service;

import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.PaymentRecord;
import com.house.repo.BillRepository;
import com.house.repo.PaymentRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 收款状态流转测试。
 *
 * <p>覆盖「部分收款 → 已付清」的累计金额判断、重复收款与作废账单拦截，
 * 这些直接决定账单状态是否正确。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

  @Mock private PaymentRecordRepository paymentRecordRepository;
  @Mock private BillRepository billRepository;

  @InjectMocks private PaymentService paymentService;

  private Bill bill;

  @BeforeEach
  void setUp() {
    bill = Bill.builder()
        .id(1L)
        .totalAmount(3000.0)
        .status(BillStatus.PENDING)
        .build();
    when(billRepository.findById(1L)).thenReturn(Optional.of(bill));
    when(billRepository.save(any(Bill.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRecordRepository.save(any(PaymentRecord.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private com.house.dto.PaymentRequest req(double amount) {
    var r = new com.house.dto.PaymentRequest();
    r.setPaidAmount(amount);
    r.setPaymentMethod("CASH");
    return r;
  }

  @Test
  @DisplayName("部分收款后状态为 PARTIAL")
  void partialPayment() {
    when(paymentRecordRepository.findByBillId(1L)).thenReturn(List.of(
        PaymentRecord.builder().billId(1L).paidAmount(1000.0).build()
    ));

    paymentService.recordPayment(1L, req(1000.0));

    assertEquals(BillStatus.PARTIAL, bill.getStatus());
  }

  @Test
  @DisplayName("累计收款达到总额时状态为 PAID")
  void fullPaymentAccumulates() {
    when(paymentRecordRepository.findByBillId(1L)).thenReturn(List.of(
        PaymentRecord.builder().billId(1L).paidAmount(1000.0).build(),
        PaymentRecord.builder().billId(1L).paidAmount(2000.0).build()
    ));

    paymentService.recordPayment(1L, req(2000.0));

    assertEquals(BillStatus.PAID, bill.getStatus());
  }

  @Test
  @DisplayName("超额收款也视为已付清")
  void overPaymentMarksPaid() {
    when(paymentRecordRepository.findByBillId(1L)).thenReturn(List.of(
        PaymentRecord.builder().billId(1L).paidAmount(3500.0).build()
    ));

    paymentService.recordPayment(1L, req(3500.0));

    assertEquals(BillStatus.PAID, bill.getStatus());
  }

  @Test
  @DisplayName("已付清账单不能再收款")
  void rejectPaymentOnPaidBill() {
    bill.setStatus(BillStatus.PAID);

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> paymentService.recordPayment(1L, req(100.0)));
    assertTrue(ex.getMessage().contains("已付清"));
    verify(paymentRecordRepository, never()).save(any());
  }

  @Test
  @DisplayName("中文状态的历史已付清账单同样拦截")
  void rejectPaymentOnChinesePaidBill() {
    bill.setStatus("已支付");

    assertThrows(IllegalStateException.class,
        () -> paymentService.recordPayment(1L, req(100.0)));
  }

  @Test
  @DisplayName("已作废账单不能收款")
  void rejectPaymentOnVoidBill() {
    bill.setStatus(BillStatus.VOID);

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> paymentService.recordPayment(1L, req(100.0)));
    assertTrue(ex.getMessage().contains("作废"));
  }

  @Test
  @DisplayName("收款金额必须大于 0")
  void rejectNonPositiveAmount() {
    assertThrows(IllegalArgumentException.class,
        () -> paymentService.recordPayment(1L, req(0)));
    assertThrows(IllegalArgumentException.class,
        () -> paymentService.recordPayment(1L, req(-50)));
  }

  @Test
  @DisplayName("账单不存在时报可读错误")
  void rejectMissingBill() {
    when(billRepository.findById(99L)).thenReturn(Optional.empty());

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> paymentService.recordPayment(99L, req(100.0)));
    assertEquals("账单不存在", ex.getMessage());
  }

  // ---------- 批量收款 ----------

  @Test
  @DisplayName("批量收款按剩余应收全额收取")
  void batchPaymentUsesRemainingAmount() {
    Bill b1 = Bill.builder().id(1L).totalAmount(3000.0).status(BillStatus.PENDING).build();
    Bill b2 = Bill.builder().id(2L).totalAmount(2000.0).status(BillStatus.PARTIAL).build();
    when(billRepository.findById(1L)).thenReturn(Optional.of(b1));
    when(billRepository.findById(2L)).thenReturn(Optional.of(b2));
    when(paymentRecordRepository.findByBillId(1L)).thenReturn(List.of());
    when(paymentRecordRepository.findByBillId(2L)).thenReturn(List.of(
        PaymentRecord.builder().billId(2L).paidAmount(500.0).build()
    ));

    Map<String, Object> result = paymentService.recordBatchPayment(
        List.of(1L, 2L), "WECHAT", null, "批量收款");

    assertEquals(2, result.get("successCount"));
    assertEquals(0, result.get("failCount"));
    // 3000 + (2000 - 500) = 4500
    assertEquals(4500.0, result.get("totalPaid"));
    assertEquals(BillStatus.PAID, b1.getStatus());
    assertEquals(BillStatus.PAID, b2.getStatus());
  }

  @Test
  @DisplayName("批量收款跳过已付清账单并记录原因")
  void batchPaymentSkipsPaidBills() {
    Bill paid = Bill.builder().id(1L).totalAmount(3000.0).status(BillStatus.PAID).build();
    when(billRepository.findById(1L)).thenReturn(Optional.of(paid));

    Map<String, Object> result = paymentService.recordBatchPayment(
        List.of(1L), "CASH", null, null);

    assertEquals(0, result.get("successCount"));
    assertEquals(1, result.get("failCount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
    assertTrue(failures.get(0).get("reason").toString().contains("已付清"));
  }

  @Test
  @DisplayName("批量收款空列表报错")
  void batchPaymentRequiresIds() {
    assertThrows(IllegalArgumentException.class,
        () -> paymentService.recordBatchPayment(List.of(), "CASH", null, null));
    assertThrows(IllegalArgumentException.class,
        () -> paymentService.recordBatchPayment(null, "CASH", null, null));
  }
}
