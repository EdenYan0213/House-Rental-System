package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sign_agreement")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignAgreement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "verify_status", nullable = false, length = 20)
  private String verifyStatus; // PENDING/PASSED/FAILED

  @Column(name = "verify_name", length = 50)
  private String verifyName;

  @Column(name = "verify_id_card", length = 50)
  private String verifyIdCard;

  @Column(name = "sign_status", nullable = false, length = 20)
  private String signStatus; // UNSIGNED/SIGNED/FAILED/TIMEOUT

  @Column(name = "sign_provider", length = 50)
  private String signProvider;

  @Column(name = "sign_flow_no", length = 100)
  private String signFlowNo;

  @Column(name = "signed_at")
  private LocalDateTime signedAt;

  @Column(name = "expire_at")
  private LocalDateTime expireAt;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
