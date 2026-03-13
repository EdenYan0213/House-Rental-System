package com.house.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  private String status; // 待生效/进行中/已到期/已解约

  @Column(name = "contract_no")
  private String contractNo;

  @Column(name = "payment_method")
  private String paymentMethod;

  private String remark;

  @Column(name = "attachment_url")
  private String attachmentUrl;

  private Double deposit;

  @Column(name = "monthly_rent")
  private Double monthlyRent;

  @Transient
  private String roomNumber;
  @Transient
  private String buildingName;
  @Transient
  private String tenantName;
  @Transient
  private String tenantPhone;

  @Column(name = "reminder_sent")
  private String reminderSent;

  private Integer deleted;

  public void setId(Long id) { this.id = id; }
  public Long getId() { return id; }
  public void setRoomId(Long roomId) { this.roomId = roomId; }
  public Long getRoomId() { return roomId; }
  public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
  public Long getTenantId() { return tenantId; }
  public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
  public LocalDate getStartDate() { return startDate; }
  public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
  public LocalDate getEndDate() { return endDate; }
  public void setStatus(String status) { this.status = status; }
  public String getStatus() { return status; }
  public void setContractNo(String contractNo) { this.contractNo = contractNo; }
  public String getContractNo() { return contractNo; }
  public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
  public String getPaymentMethod() { return paymentMethod; }
  public void setRemark(String remark) { this.remark = remark; }
  public String getRemark() { return remark; }
  public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
  public String getAttachmentUrl() { return attachmentUrl; }
  public void setDeposit(Double deposit) { this.deposit = deposit; }
  public Double getDeposit() { return deposit; }
  public void setMonthlyRent(Double monthlyRent) { this.monthlyRent = monthlyRent; }
  public Double getMonthlyRent() { return monthlyRent; }
  public void setReminderSent(String reminderSent) { this.reminderSent = reminderSent; }
  public String getReminderSent() { return reminderSent; }
  public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
  public String getRoomNumber() { return this.roomNumber; }
  public void setBuildingName(String buildingName) { this.buildingName = buildingName; }
  public String getBuildingName() { return this.buildingName; }
  public void setTenantName(String tenantName) { this.tenantName = tenantName; }
  public String getTenantName() { return this.tenantName; }
  public void setTenantPhone(String tenantPhone) { this.tenantPhone = tenantPhone; }
  public String getTenantPhone() { return this.tenantPhone; }
  public void setDeleted(Integer deleted) { this.deleted = deleted; }
  public Integer getDeleted() { return deleted; }
}
