package com.house.repo;

import com.house.model.DepositRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositRecordRepository extends JpaRepository<DepositRecord, Long> {
  /** 按合同批量取押金记录（租客端只看自己名下合同的）。 */
  List<DepositRecord> findByContractIdIn(List<Long> contractIds);
}
