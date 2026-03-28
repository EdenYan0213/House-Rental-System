package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "room_id")
  private Long roomId;

  @Column(nullable = false, length = 20)
  private String category; // TODO/NOTICE

  @Column(nullable = false, length = 50)
  private String type; // OVERDUE/REPAIR_PROGRESS/...

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "target_url", length = 300)
  private String targetUrl;

  @Column(name = "ref_type", length = 50)
  private String refType;

  @Column(name = "ref_id")
  private Long refId;

  @Column(name = "is_read", nullable = false)
  private Integer isRead;

  @Column(name = "is_deleted", nullable = false)
  private Integer isDeleted;

  @Column(name = "need_action", nullable = false)
  private Integer needAction;

  @Column(name = "action_status", nullable = false, length = 20)
  private String actionStatus; // PENDING/DONE

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}
