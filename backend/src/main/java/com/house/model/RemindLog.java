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

  /**
   * 短信场景。同一张表既存催缴短信也存验证码短信，
   * 管理端「催缴记录」只查 REMIND，避免验证码把列表刷满。
   */
  public static final String SCENE_REMIND = "REMIND";
  public static final String SCENE_LOGIN_CODE = "LOGIN_CODE";
  public static final String SCENE_UPDATE_PHONE = "UPDATE_PHONE";
  public static final String SCENE_TEST = "TEST";

  @Column(name = "scene")
  @Builder.Default
  private String scene = SCENE_REMIND;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
