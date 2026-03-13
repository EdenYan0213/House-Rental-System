package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appliance_template")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplianceTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String brand;

  @Column(name = "default_quantity")
  private Integer defaultQuantity;

  private String unit;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
