package com.house.repo;

import com.house.model.RemindRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RemindRuleRepository extends JpaRepository<RemindRule, Integer> {
  List<RemindRule> findByIsEnabled(Integer isEnabled);
}
