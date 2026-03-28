package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "repair_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_no")
  private String orderNo;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "reporter_type")
  private String reporterType;

  @Column(name = "reported_by")
  private Long reportedBy;

  @Column(name = "fault_type")
  private String faultType;

  @Column(columnDefinition = "text")
  private String description;

  @Column(columnDefinition = "text")
  private String images;

  @Column(name = "appliance_id")
  private Long applianceId;

  private String status;

  @Column(name = "worker_id")
  private Long workerId;

  @Column(name = "assigned_at")
  private LocalDateTime assignedAt;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "finish_time")
  private LocalDateTime finishTime;

  @Column(name = "expected_time")
  private LocalDateTime expectedTime;

  @Column(name = "contact_phone")
  private String contactPhone;

  @Column(name = "accept_time")
  private LocalDateTime acceptTime;

  @Column(name = "cancel_reason", columnDefinition = "text")
  private String cancelReason;

  @Column(name = "repair_result", columnDefinition = "text")
  private String repairResult;

  @Column(name = "material_fee")
  private Double materialFee;

  @Column(name = "labor_fee")
  private Double laborFee;

  @Column(name = "total_fee")
  private Double totalFee;

  @Column(name = "fee_remark", columnDefinition = "text")
  private String feeRemark;

  @Column(name = "is_tenant_charge")
  private Integer isTenantCharge;

  @Column(name = "bill_item_id")
  private Long billItemId;

  private Integer rating;

  @Column(columnDefinition = "text")
  private String comment;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Transient
  private String roomNumber;

  @Transient
  private String tenantName;

  @Transient
  private String workerName;

  @Transient
  private String applianceName;
}
