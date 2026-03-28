package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_login_code")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantLoginCode {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(nullable = false, length = 6)
  private String code;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "sent_date", nullable = false)
  private LocalDate sentDate;

  @Column(name = "sent_count", nullable = false)
  private Integer sentCount;

  @Column(name = "last_sent_at", nullable = false)
  private LocalDateTime lastSentAt;
}
