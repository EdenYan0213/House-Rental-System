package com.house.repo;

import java.util.List;

import com.house.model.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, Long> {
  List<Bill> findByBillMonth(String billMonth);

  List<Bill> findByContractId(Long contractId);

  List<Bill> findByContractIdIn(List<Long> contractIds);

  java.util.Optional<Bill> findByContractIdAndBillMonth(Long contractId, String billMonth);
}
