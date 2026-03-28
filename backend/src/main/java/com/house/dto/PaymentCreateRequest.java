package com.house.dto;

import lombok.Data;

@Data
public class PaymentCreateRequest {
  private Long billId;
  private String payType;
}
