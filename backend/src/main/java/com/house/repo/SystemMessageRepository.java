package com.house.repo;

import com.house.model.SystemMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemMessageRepository extends JpaRepository<SystemMessage, Long> {
  Page<SystemMessage> findByIsRead(Boolean isRead, Pageable pageable);

  long countByIsRead(Boolean isRead);

  Optional<SystemMessage> findTopByOrderByCreatedAtDesc();
}
