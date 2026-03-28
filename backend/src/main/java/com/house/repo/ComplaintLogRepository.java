package com.house.repo;

import com.house.model.ComplaintLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintLogRepository extends JpaRepository<ComplaintLog, Long> {
  List<ComplaintLog> findByComplaintIdOrderByCreatedAtDesc(Long complaintId);
}
