package com.house.repo;

import com.house.model.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, Long> {
    List<BackupRecord> findAllByOrderByBackupTimeDesc();
    List<BackupRecord> findByTypeOrderByBackupTimeDesc(String type);
    List<BackupRecord> findByStatusOrderByBackupTimeDesc(String status);
    List<BackupRecord> findByTypeAndStatusOrderByBackupTimeDesc(String type, String status);
    long countByType(String type);
    long countByTypeAndStatus(String type, String status);
}
