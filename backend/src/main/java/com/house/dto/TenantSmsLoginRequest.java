package com.house.dto;

import lombok.Data;

@Data
public class TenantSmsLoginRequest {
  private String phone;
  private String code;
}
