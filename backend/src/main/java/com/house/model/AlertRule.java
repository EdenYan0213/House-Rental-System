package com.house.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rule")
@Data
public class AlertRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "rule_type", nullable = false, length = 50)
  private String ruleType;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "enabled", nullable = false)
  private Integer enabled = 1;

  @Column(name = "threshold_value")
  private Integer thresholdValue;

  @Column(name = "window_minutes")
  private Integer windowMinutes;

  @Column(name = "level", nullable = false)
  private Integer level = 2;

  @Column(name = "notify_channels", length = 255)
  private String notifyChannels;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
