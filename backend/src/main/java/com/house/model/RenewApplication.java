package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "renew_application")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenewApplication {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "expected_duration", nullable = false)
  private Integer expectedDuration;

  @Column(columnDefinition = "text")
  private String remark;

  @Column(nullable = false)
  private Integer status;

  @Column(name = "result_remark", columnDefinition = "text")
  private String resultRemark;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
