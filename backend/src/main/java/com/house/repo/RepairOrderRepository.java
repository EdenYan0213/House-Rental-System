package com.house.repo;

import com.house.model.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long>, JpaSpecificationExecutor<RepairOrder> {
  List<RepairOrder> findByRoomIdOrderByCreatedAtDesc(Long roomId);

  List<RepairOrder> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

  long countByWorkerId(Long workerId);
}
