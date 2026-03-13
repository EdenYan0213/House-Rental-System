package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "repair_worker")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairWorker {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private String phone;

  private Integer status;

  private String remark;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
