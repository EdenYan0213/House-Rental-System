package com.house.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_log")
@Data
public class OperationLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "username")
  private String username;

  @Column(name = "operation_time")
  private LocalDateTime operationTime;

  @Column(name = "operator")
  private String operator;

  @Column(name = "module")
  private String module;

  @Column(name = "action")
  private String action;

  @Column(name = "business_id")
  private String businessId;

  @Column(name = "business_name")
  private String businessName;

  @Column(name = "detail_before", columnDefinition = "json")
  private String detailBefore;

  @Column(name = "detail_after", columnDefinition = "json")
  private String detailAfter;

  @Column(name = "change_summary", columnDefinition = "TEXT")
  private String changeSummary;

  @Column(name = "request_params", columnDefinition = "TEXT")
  private String requestParams;

  @Column(name = "request_ip")
  private String requestIp;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "result")
  private Integer result;

  @Column(name = "fail_reason", columnDefinition = "TEXT")
  private String failReason;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "log_level")
  private Integer logLevel;

  @Column(name = "ip")
  private String ip;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "ai_instruction", columnDefinition = "TEXT")
  private String aiInstruction;

  @Column(name = "ai_intent")
  private String aiIntent;

  @Column(name = "ai_entities", columnDefinition = "TEXT")
  private String aiEntities;

  @Column(name = "ai_status")
  private String aiStatus;

  @Column(name = "ai_response_message", columnDefinition = "TEXT")
  private String aiResponseMessage;

  @Column(name = "ai_duration_ms")
  private Long aiDurationMs;

  @Column(name = "ai_model_name")
  private String aiModelName;
}
