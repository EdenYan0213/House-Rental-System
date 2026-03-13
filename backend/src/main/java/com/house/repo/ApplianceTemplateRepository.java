package com.house.repo;

import com.house.model.ApplianceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplianceTemplateRepository extends JpaRepository<ApplianceTemplate, Long> {
  List<ApplianceTemplate> findAllByOrderBySortOrderAscIdAsc();
}
