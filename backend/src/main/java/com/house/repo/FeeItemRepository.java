package com.house.repo;

import com.house.model.FeeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeeItemRepository extends JpaRepository<FeeItem, Long> {
  Optional<FeeItem> findByName(String name);
}
