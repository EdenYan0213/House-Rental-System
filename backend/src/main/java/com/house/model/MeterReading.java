package com.house.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReading {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long roomId;
  private LocalDate recordDate;
  private Double waterReading; // 表示累计读数
  private Double electricReading; // 表示累计读数
}
