package com.house.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillDetail {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long billId;
  private Long feeItemId;
  private String name;
  private Double usageAmount;
  private Double amount;

  @Transient
  private Double prevReading;

  @Transient
  private Double currReading;
}
