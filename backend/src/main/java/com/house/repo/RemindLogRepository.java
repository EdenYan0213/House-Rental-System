package com.house.repo;

import com.house.model.RemindLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RemindLogRepository extends JpaRepository<RemindLog, Long> {
  List<RemindLog> findByTenantIdAndOverdueDays(Long tenantId, Integer overdueDays);
}
