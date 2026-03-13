package com.house.repo;
import java.util.List;

import com.house.model.DepositRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRecordRepository extends JpaRepository<DepositRecord, Long> {
}
