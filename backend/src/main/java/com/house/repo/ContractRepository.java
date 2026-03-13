package com.house.repo;

import java.util.List;

import com.house.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
  boolean existsByRoomId(Long roomId);

  List<Contract> findByTenantId(Long tenantId);

  List<Contract> findByStatus(String status);

  List<Contract> findByRoomIdAndStatus(Long roomId, String status);

  List<Contract> findByRoomIdInAndStatus(List<Long> roomIds, String status);
}
