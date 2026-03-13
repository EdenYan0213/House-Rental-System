package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long billId;
  private Double paidAmount;
  private LocalDateTime paidDate;
  private String paymentMethod;
  private String remark;
}
