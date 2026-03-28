package com.house.repo;

import com.house.model.DepositRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRecordRepository extends JpaRepository<DepositRecord, Long> {
}
