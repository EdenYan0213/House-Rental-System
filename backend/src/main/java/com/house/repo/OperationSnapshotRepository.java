package com.house.repo;

import com.house.model.OperationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationSnapshotRepository
    extends JpaRepository<OperationSnapshot, Long>, JpaSpecificationExecutor<OperationSnapshot> {
  void deleteByCreatedAtBefore(java.time.LocalDateTime cutoff);

  void deleteByExpiredAtBeforeAndIsRecoveredFalse(java.time.LocalDateTime now);
}
