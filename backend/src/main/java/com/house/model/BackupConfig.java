package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "backup_config")
public class BackupConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Boolean enabled;

    @Column(name = "backup_type")
    private String backupType;

    @Column(name = "backup_day")
    private Integer backupDay;

    @Column(name = "backup_time")
    private String backupTime;

    @Column(name = "retention_count")
    private Integer retentionCount;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getEnabled() { return enabled; }
    public void setBackupType(String backupType) { this.backupType = backupType; }
    public String getBackupType() { return backupType; }
    public void setBackupDay(Integer backupDay) { this.backupDay = backupDay; }
    public Integer getBackupDay() { return backupDay; }
    public void setBackupTime(String backupTime) { this.backupTime = backupTime; }
    public String getBackupTime() { return backupTime; }
    public void setRetentionCount(Integer retentionCount) { this.retentionCount = retentionCount; }
    public Integer getRetentionCount() { return retentionCount; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getStoragePath() { return storagePath; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
}
