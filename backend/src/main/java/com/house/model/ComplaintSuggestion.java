package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "complaint_suggestion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintSuggestion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "complaint_no")
  private String complaintNo;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "reporter_type")
  private String reporterType;

  @Column(name = "reported_by")
  private Long reportedBy;

  private String type;

  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(columnDefinition = "json")
  private String images;

  @Column(name = "is_anonymous")
  private Integer isAnonymous;

  private String priority;

  private String status;

  @Column(name = "assigned_to")
  private Long assignedTo;

  @Column(name = "assigned_at")
  private LocalDateTime assignedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Column(name = "closed_reason", columnDefinition = "text")
  private String closedReason;

  private Integer rating;

  @Column(columnDefinition = "text")
  private String comment;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Transient
  private String tenantName;

  @Transient
  private String tenantPhone;

  @Transient
  private String tenantDisplayName;

  @Transient
  private String roomNumber;

  @Transient
  private Long buildingId;

  @Transient
  private String buildingName;

  @Transient
  private String assignedName;

  @Transient
  private List<ComplaintLog> logs;
}
