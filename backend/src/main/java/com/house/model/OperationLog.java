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
}
