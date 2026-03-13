package com.house.repo;
import java.util.List;

import com.house.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
  List<Tenant> findByNameContainingOrPhoneContaining(String name, String phone);
}
