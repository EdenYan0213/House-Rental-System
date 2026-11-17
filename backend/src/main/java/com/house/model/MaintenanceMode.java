package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "maintenance_mode")
public class MaintenanceMode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean enabled;

    private String reason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "started_at")
    private java.time.LocalDateTime startedAt;

    @Column(name = "ended_at")
    private java.time.LocalDateTime endedAt;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getEnabled() { return enabled; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReason() { return reason; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Long getOperatorId() { return operatorId; }
    public void setStartedAt(java.time.LocalDateTime startedAt) { this.startedAt = startedAt; }
    public java.time.LocalDateTime getStartedAt() { return startedAt; }
    public void setEndedAt(java.time.LocalDateTime endedAt) { this.endedAt = endedAt; }
    public java.time.LocalDateTime getEndedAt() { return endedAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
}
