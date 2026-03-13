package com.house.repo;

import com.house.model.ReminderSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderSettingsRepository extends JpaRepository<ReminderSettings, Long> {
}
