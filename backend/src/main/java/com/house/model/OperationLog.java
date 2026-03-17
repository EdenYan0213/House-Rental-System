package com.house.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_log")
public class OperationLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "operation_time")
  private LocalDateTime operationTime;

  @Column(name = "operator")
  private String operator;

  @Column(name = "module")
  private String module;

  @Column(name = "action")
  private String action;

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

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDateTime getOperationTime() {
    return operationTime;
  }

  public void setOperationTime(LocalDateTime operationTime) {
    this.operationTime = operationTime;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getAiInstruction() {
    return aiInstruction;
  }

  public void setAiInstruction(String aiInstruction) {
    this.aiInstruction = aiInstruction;
  }

  public String getAiIntent() {
    return aiIntent;
  }

  public void setAiIntent(String aiIntent) {
    this.aiIntent = aiIntent;
  }

  public String getAiEntities() {
    return aiEntities;
  }

  public void setAiEntities(String aiEntities) {
    this.aiEntities = aiEntities;
  }

  public String getAiStatus() {
    return aiStatus;
  }

  public void setAiStatus(String aiStatus) {
    this.aiStatus = aiStatus;
  }

  public String getAiResponseMessage() {
    return aiResponseMessage;
  }

  public void setAiResponseMessage(String aiResponseMessage) {
    this.aiResponseMessage = aiResponseMessage;
  }

  public Long getAiDurationMs() {
    return aiDurationMs;
  }

  public void setAiDurationMs(Long aiDurationMs) {
    this.aiDurationMs = aiDurationMs;
  }

  public String getAiModelName() {
    return aiModelName;
  }

  public void setAiModelName(String aiModelName) {
    this.aiModelName = aiModelName;
  }
}
