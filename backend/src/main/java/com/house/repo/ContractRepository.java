package com.house.repo;

import java.util.List;

import com.house.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {
  boolean existsByRoomId(Long roomId);

  List<Contract> findByTenantId(Long tenantId);

  List<Contract> findByStatus(String status);

  List<Contract> findByTenantIdAndStatusIn(Long tenantId, List<String> statuses);

  List<Contract> findByTenantIdAndRoomId(Long tenantId, Long roomId);

  List<Contract> findByTenantIdOrderByStartDateDesc(Long tenantId);

  List<Contract> findByTenantIdAndRoomIdOrderByStartDateDesc(Long tenantId, Long roomId);

  List<Contract> findByRoomId(Long roomId);

  List<Contract> findByRoomIdAndStatus(Long roomId, String status);

  List<Contract> findByRoomIdInAndStatus(List<Long> roomIds, String status);
}
