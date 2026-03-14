package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "restore_log")
public class RestoreLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restore_time")
    private java.time.LocalDateTime restoreTime;

    @Column(name = "backup_id")
    private Long backupId;

    @Column(name = "operator_id")
    private Long operatorId;

    private String status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public void setRestoreTime(java.time.LocalDateTime restoreTime) { this.restoreTime = restoreTime; }
    public java.time.LocalDateTime getRestoreTime() { return restoreTime; }
    public void setBackupId(Long backupId) { this.backupId = backupId; }
    public Long getBackupId() { return backupId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Long getOperatorId() { return operatorId; }
    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getFailReason() { return failReason; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
}
