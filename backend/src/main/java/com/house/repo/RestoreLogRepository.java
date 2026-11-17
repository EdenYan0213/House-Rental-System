package com.house.repo;

import com.house.model.RestoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestoreLogRepository extends JpaRepository<RestoreLog, Long> {
    List<RestoreLog> findAllByOrderByRestoreTimeDesc();
    List<RestoreLog> findByBackupIdOrderByRestoreTimeDesc(Long backupId);
    List<RestoreLog> findByStatusOrderByRestoreTimeDesc(String status);
}
