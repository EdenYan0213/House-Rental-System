package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 图片二进制存储。图片直接存 MySQL，避免落盘带来的孤儿文件与部署问题。
 *
 * <p>上传接口只负责把图片写入本表并返回 ID；业务表（楼栋/房间）保存时
 * 才把 ID 列表写入 image_ids。因此「上传后取消编辑」的图片 ref_count 为 0，
 * 可被定时任务清理。
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "image_blob")
public class ImageBlob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "original_name", length = 255)
  private String originalName;

  /** 原磁盘文件名（UUID_原名）。仅用于兼容旧 `/api/files/<name>` URL，新上传不再落盘。 */
  @Column(name = "stored_name", length = 255)
  private String storedName;

  @Lob
  @Column(name = "data", nullable = false, columnDefinition = "LONGBLOB")
  private byte[] data;

  /** 被引用次数。0 表示上传后未被任何业务数据保存，可清理。 */
  @Column(name = "ref_count", nullable = false)
  private Integer refCount;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    if (refCount == null) {
      refCount = 0;
    }
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (fileSize == null && data != null) {
      fileSize = (long) data.length;
    }
  }
}
