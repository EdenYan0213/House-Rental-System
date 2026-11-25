package com.house.repo;

import com.house.model.RemindLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RemindLogRepository extends JpaRepository<RemindLog, Long> {
  List<RemindLog> findByTenantIdAndOverdueDays(Long tenantId, Integer overdueDays);

  /** 催缴记录：只看催缴场景，验证码短信不进这个列表。 */
  List<RemindLog> findBySceneOrderByCreatedAtDesc(String scene);

  /** 全部短信记录（含验证码），用于运营排查。 */
  List<RemindLog> findAllByOrderByCreatedAtDesc();
}
