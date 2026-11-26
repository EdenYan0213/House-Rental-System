package com.house.repo;

import java.util.List;

import com.house.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<Contract, Long> {
  boolean existsByRoomId(Long roomId);

  List<Contract> findByTenantId(Long tenantId);

  List<Contract> findByStatus(String status);

  /** 用 {@code ContractStatus.ACTIVE_RAW_VALUES} 调用，兼容历史数据里“进行中”的旧写法。 */
  List<Contract> findByStatusIn(List<String> statuses);

  List<Contract> findByTenantIdAndStatusIn(Long tenantId, List<String> statuses);

  List<Contract> findByTenantIdAndRoomId(Long tenantId, Long roomId);

  List<Contract> findByTenantIdOrderByStartDateDesc(Long tenantId);

  List<Contract> findByTenantIdAndRoomIdOrderByStartDateDesc(Long tenantId, Long roomId);

  List<Contract> findByRoomId(Long roomId);

  List<Contract> findByRoomIdAndStatus(Long roomId, String status);

  /** 一次查出生效中的合同，兼容“进行中”旧写法；替代“先查 ACTIVE 再查 进行中”的两次查询。 */
  List<Contract> findByRoomIdAndStatusIn(Long roomId, List<String> statuses);

  List<Contract> findByRoomIdInAndStatus(List<Long> roomIds, String status);
}
