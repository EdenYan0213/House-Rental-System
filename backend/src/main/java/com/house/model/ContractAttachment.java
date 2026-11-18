package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 合同附件版本历史。
 *
 * <p>合同正文/证件每替换一次就新增一条记录，version 递增，
 * 用于追溯「某个时间点签的是哪一版文件」。
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "contract_attachment")
public class ContractAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  /** 版本号，同一合同内从 1 递增。 */
  private Integer version;

  @Column(name = "file_url", nullable = false, length = 500)
  private String fileUrl;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "file_size")
  private Long fileSize;

  /** CONTRACT 合同正文 / ID_CARD 证件 / OTHER 其他 */
  private String category;

  @Column(length = 500)
  private String remark;

  @Column(length = 50)
  private String operator;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (version == null) {
      version = 1;
    }
  }
}
