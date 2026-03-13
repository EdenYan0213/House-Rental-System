package com.house.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ContractCreateRequest {
  private Long roomId;
  private Long tenantId; // 可选

  // 若为新租户，可传下述字段
  private String tenantName;
  private String tenantPhone;
  private String tenantIdCard;
  private String tenantEmergencyContact;
  private String tenantEmergencyPhone;
  private String tenantRemark;

  private LocalDate startDate;
  private LocalDate endDate;
  private Double deposit;
  private Double monthlyRent;
  private String paymentMethod;
  private String remark;
  private String attachmentUrl;
}
