package com.house.repo;

import com.house.model.TenantLoginCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantLoginCodeRepository extends JpaRepository<TenantLoginCode, Long> {
  Optional<TenantLoginCode> findByPhone(String phone);
}
