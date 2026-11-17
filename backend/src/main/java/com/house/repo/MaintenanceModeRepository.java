package com.house.repo;

import com.house.model.MaintenanceMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaintenanceModeRepository extends JpaRepository<MaintenanceMode, Long> {
    Optional<MaintenanceMode> findTopByEnabledTrueOrderByStartedAtDesc();
}
