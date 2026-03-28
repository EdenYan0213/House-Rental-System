package com.house.repo;

import com.house.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
  List<PaymentRecord> findByBillId(Long billId);
}
