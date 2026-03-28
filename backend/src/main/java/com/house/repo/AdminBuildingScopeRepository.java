package com.house.repo;

import com.house.model.AdminBuildingScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminBuildingScopeRepository extends JpaRepository<AdminBuildingScope, Long> {
  List<AdminBuildingScope> findByAdminId(Long adminId);
}
