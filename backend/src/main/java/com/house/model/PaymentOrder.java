package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bill_id", nullable = false)
  private Long billId;

  @Column(nullable = false)
  private Double amount;

  @Column(name = "pay_type", nullable = false, length = 20)
  private String payType;

  @Column(name = "out_trade_no", nullable = false, unique = true, length = 64)
  private String outTradeNo;

  @Column(name = "transaction_id", length = 64)
  private String transactionId;

  @Column(nullable = false)
  private Integer status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
