package com.house.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_record")
@Data
public class AlertRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "alert_type", nullable = false, length = 50)
  private String alertType;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "level", nullable = false)
  private Integer level;

  @Column(name = "status", nullable = false)
  private Integer status = 0;

  @Column(name = "operator_id")
  private Integer operatorId;

  @Column(name = "handled_at")
  private LocalDateTime handledAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
