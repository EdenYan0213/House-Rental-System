package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "backup_record")
public class BackupRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "backup_time")
    private java.time.LocalDateTime backupTime;

    private String type;

    private String status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    private String md5;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public void setBackupTime(java.time.LocalDateTime backupTime) { this.backupTime = backupTime; }
    public java.time.LocalDateTime getBackupTime() { return backupTime; }
    public void setType(String type) { this.type = type; }
    public String getType() { return type; }
    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileName() { return fileName; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFilePath() { return filePath; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getFileSize() { return fileSize; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getMd5() { return md5; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getFailReason() { return failReason; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Long getOperatorId() { return operatorId; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
}
