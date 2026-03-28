package com.house.repo;

import com.house.model.RenewApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RenewApplicationRepository extends JpaRepository<RenewApplication, Long> {
  List<RenewApplication> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
