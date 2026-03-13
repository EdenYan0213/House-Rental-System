package com.house.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentRequest {
  private Double paidAmount;
  private LocalDateTime paidDate; // 可选，空则使用当前时间
  private String paymentMethod;
  private String remark;
}
