package com.house.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_settings")
public class ReminderSettings {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "enabled")
  private Boolean enabled;

  @Column(name = "reminder_days", length = 100)
  private String reminderDays;

  @Column(name = "reminder_time", length = 10)
  private String reminderTime;

  @Column(name = "overdue_reminder_enabled")
  private Boolean overdueReminderEnabled;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public ReminderSettings() {}

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Boolean getEnabled() { return enabled; }
  public void setEnabled(Boolean enabled) { this.enabled = enabled; }
  public String getReminderDays() { return reminderDays; }
  public void setReminderDays(String reminderDays) { this.reminderDays = reminderDays; }
  public String getReminderTime() { return reminderTime; }
  public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }
  public Boolean getOverdueReminderEnabled() { return overdueReminderEnabled; }
  public void setOverdueReminderEnabled(Boolean overdueReminderEnabled) { this.overdueReminderEnabled = overdueReminderEnabled; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
