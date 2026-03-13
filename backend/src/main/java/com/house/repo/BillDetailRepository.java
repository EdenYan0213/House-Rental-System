package com.house.repo;
import java.util.List;

import com.house.model.BillDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillDetailRepository extends JpaRepository<BillDetail, Long> {
  List<BillDetail> findByBillId(Long billId);
}
