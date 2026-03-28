package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notice")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(length = 500)
  private String image;

  @Column(nullable = false)
  private Integer sort;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
