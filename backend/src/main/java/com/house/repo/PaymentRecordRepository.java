package com.house.repo;

import com.house.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
  List<PaymentRecord> findByBillId(Long billId);

  /** 批量取多张账单的收款记录，避免逐条查询（N+1）。 */
  List<PaymentRecord> findByBillIdIn(List<Long> billIds);
}
