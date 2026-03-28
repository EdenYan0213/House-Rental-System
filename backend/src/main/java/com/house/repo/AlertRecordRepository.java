package com.house.repo;

import com.house.model.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;

public interface AlertRecordRepository
    extends JpaRepository<AlertRecord, Integer>, JpaSpecificationExecutor<AlertRecord> {
  boolean existsByAlertTypeAndTitleAndCreatedAtAfter(String alertType, String title, LocalDateTime createdAt);
}
