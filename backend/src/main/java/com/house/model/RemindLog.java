package com.house.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemindLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "tenant_name")
  private String tenantName;

  private String phone;

  @Column(name = "room_number")
  private String roomNumber;

  @Column(name = "overdue_days")
  private Integer overdueDays;

  @Column(name = "bill_ids", columnDefinition = "TEXT")
  private String billIds; // JSON array string

  @Column(name = "total_amount")
  private Double totalAmount;

  @Column(columnDefinition = "TEXT")
  private String content;

  private String status; // success / failed

  @Column(name = "fail_reason", columnDefinition = "TEXT")
  private String failReason;

  @Column(name = "is_debug")
  private Integer isDebug;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
