package com.house.controller;

import com.house.model.ReminderSettings;
import com.house.repo.ReminderSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/reminder-settings")
public class ReminderSettingsController {
  private final ReminderSettingsRepository reminderSettingsRepository;

  public ReminderSettingsController(ReminderSettingsRepository reminderSettingsRepository) {
    this.reminderSettingsRepository = reminderSettingsRepository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public ResponseEntity<ReminderSettings> get() {
    // 全局只有一行配置，存在即返回
    return reminderSettingsRepository.findAll().stream()
        .findFirst()
        .map(ResponseEntity::ok)
        .orElseGet(() -> {
          // 首次访问时落默认值。注意：@GeneratedValue(IDENTITY) 下不能预设 id，
          // 否则 Hibernate 按 update 处理不存在的行，抛 ObjectOptimisticLockingFailureException。
          ReminderSettings defaultSettings = new ReminderSettings();
          defaultSettings.setEnabled(true);
          defaultSettings.setReminderDays("3,1");
          defaultSettings.setReminderTime("09:00");
          defaultSettings.setOverdueReminderEnabled(false);
          return ResponseEntity.ok(reminderSettingsRepository.save(defaultSettings));
        });
  }

  @PutMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
  public ResponseEntity<ReminderSettings> update(@RequestBody ReminderSettings settings) {
    // 沿用已存在行的主键，没有则让数据库生成
    Long existingId = reminderSettingsRepository.findAll().stream()
        .findFirst()
        .map(ReminderSettings::getId)
        .orElse(null);
    settings.setId(existingId);
    return ResponseEntity.ok(reminderSettingsRepository.save(settings));
  }
}
