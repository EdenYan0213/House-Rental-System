package com.house.repo;

import com.house.model.GuideDismiss;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuideDismissRepository extends JpaRepository<GuideDismiss, Long> {
  List<GuideDismiss> findByUserId(Long userId);

  Optional<GuideDismiss> findByUserIdAndDismissKey(Long userId, String dismissKey);

  void deleteByUserId(Long userId);
}
