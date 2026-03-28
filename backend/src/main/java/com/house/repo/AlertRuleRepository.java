package com.house.repo;

import com.house.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Integer> {
  List<AlertRule> findByEnabled(Integer enabled);
}
