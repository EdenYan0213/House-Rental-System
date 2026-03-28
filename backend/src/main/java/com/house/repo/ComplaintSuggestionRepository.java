package com.house.repo;

import com.house.model.ComplaintSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface ComplaintSuggestionRepository
    extends JpaRepository<ComplaintSuggestion, Long>, JpaSpecificationExecutor<ComplaintSuggestion> {
  long countByTenantIdAndRoomIdAndCreatedAtBetween(Long tenantId, Long roomId, LocalDateTime start, LocalDateTime end);

  List<ComplaintSuggestion> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

  List<ComplaintSuggestion> findByTenantIdAndStatusInOrderByCreatedAtDesc(Long tenantId, List<String> statuses);
}
