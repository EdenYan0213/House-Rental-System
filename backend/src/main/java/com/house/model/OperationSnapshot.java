package com.house.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_snapshot")
public class OperationSnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  private String username;

  private String module;

  private String operation;

  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "target_name")
  private String targetName;

  @Column(name = "table_name")
  private String tableName;

  @Lob
  @Column(name = "before_data", columnDefinition = "json")
  private String beforeData;

  @Lob
  @Column(name = "after_data", columnDefinition = "json")
  private String afterData;

  @Column(name = "is_recovered")
  private Boolean isRecovered;

  @Column(name = "expired_at")
  private LocalDateTime expiredAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "recovered_at")
  private LocalDateTime recoveredAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getModule() {
    return module;
  }

  public void setModule(String module) {
    this.module = module;
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  public Long getTargetId() {
    return targetId;
  }

  public void setTargetId(Long targetId) {
    this.targetId = targetId;
  }

  public String getTargetName() {
    return targetName;
  }

  public void setTargetName(String targetName) {
    this.targetName = targetName;
  }

  public String getTableName() {
    return tableName;
  }

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public String getBeforeData() {
    return beforeData;
  }

  public void setBeforeData(String beforeData) {
    this.beforeData = beforeData;
  }

  public String getAfterData() {
    return afterData;
  }

  public void setAfterData(String afterData) {
    this.afterData = afterData;
  }

  public Boolean getIsRecovered() {
    return isRecovered;
  }

  public void setIsRecovered(Boolean recovered) {
    isRecovered = recovered;
  }

  public LocalDateTime getExpiredAt() {
    return expiredAt;
  }

  public void setExpiredAt(LocalDateTime expiredAt) {
    this.expiredAt = expiredAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getRecoveredAt() {
    return recoveredAt;
  }

  public void setRecoveredAt(LocalDateTime recoveredAt) {
    this.recoveredAt = recoveredAt;
  }
}
