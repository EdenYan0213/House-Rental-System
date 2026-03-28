package com.house.repo;

import com.house.model.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
  Page<Notice> findAllByOrderBySortDescCreatedAtDesc(Pageable pageable);

  Optional<Notice> findTopByOrderBySortDescCreatedAtDesc();
}
