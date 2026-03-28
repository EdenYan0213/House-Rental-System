package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false, length = 50)
  private String type;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(columnDefinition = "text")
  private String images;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(nullable = false)
  private Integer status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
