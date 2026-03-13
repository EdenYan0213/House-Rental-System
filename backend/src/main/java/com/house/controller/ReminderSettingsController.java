package com.house.controller;

import com.house.model.ReminderSettings;
import com.house.repo.ReminderSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/reminder-settings")
public class ReminderSettingsController {
  private final ReminderSettingsRepository reminderSettingsRepository;

  public ReminderSettingsController(ReminderSettingsRepository reminderSettingsRepository) {
    this.reminderSettingsRepository = reminderSettingsRepository;
  }

  @GetMapping
  public ResponseEntity<ReminderSettings> get() {
    Optional<ReminderSettings> settings = reminderSettingsRepository.findById(1L);
    if (settings.isEmpty()) {
      ReminderSettings defaultSettings = new ReminderSettings();
      defaultSettings.setId(1L);
      defaultSettings.setEnabled(true);
      defaultSettings.setReminderDays("3,1");
      defaultSettings.setReminderTime("09:00");
      defaultSettings.setOverdueReminderEnabled(false);
      return ResponseEntity.ok(reminderSettingsRepository.save(defaultSettings));
    }
    return ResponseEntity.ok(settings.get());
  }

  @PutMapping
  public ResponseEntity<ReminderSettings> update(@RequestBody ReminderSettings settings) {
    settings.setId(1L);
    return ResponseEntity.ok(reminderSettingsRepository.save(settings));
  }
}
