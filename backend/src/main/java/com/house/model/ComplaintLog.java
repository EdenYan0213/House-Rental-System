package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "complaint_id")
  private Long complaintId;

  @Column(name = "operator_type")
  private String operatorType;

  @Column(name = "operator_id")
  private Long operatorId;

  private String action;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Transient
  private String operatorName;
}
