package com.house.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
public class Bill {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "contract_id")
  private Long contractId;

  @Column(name = "bill_month")
  private String billMonth; // yyyy-MM

  @Column(name = "total_amount")
  private Double totalAmount;

  private String status; // 待支付/已支付/作废

  @Column(name = "generated_date")
  private LocalDateTime generatedDate;

  @Transient
  private List<PaymentRecord> payments;

  @Transient
  private Double paidAmount;

  @Transient
  private String createTime;

  @Column(name = "room_id")
  private Long roomId;

  @Column(name = "room_number")
  private String roomNumber;

  @Column(name = "building_name")
  private String buildingName;

  @Column(name = "tenant_name")
  private String tenantName;

  @Column(name = "tenant_phone")
  private String tenantPhone;

  @Transient
  private Double waterUsage;

  @Transient
  private Double electricUsage;

  @Transient
  private Double prevWaterReading;

  @Transient
  private Double currWaterReading;

  @Transient
  private Double prevElectricReading;

  @Transient
  private Double currElectricReading;

  @Transient
  private Double waterUnitPrice;

  @Transient
  private Double electricUnitPrice;

  private Integer deleted;

  public void setId(Long id) {
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public void setContractId(Long contractId) {
    this.contractId = contractId;
  }

  public Long getContractId() {
    return contractId;
  }

  public void setBillMonth(String billMonth) {
    this.billMonth = billMonth;
  }

  public String getBillMonth() {
    return billMonth;
  }

  public void setTotalAmount(Double totalAmount) {
    this.totalAmount = totalAmount;
  }

  public Double getTotalAmount() {
    return totalAmount;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatus() {
    return status;
  }

  public void setGeneratedDate(LocalDateTime generatedDate) {
    this.generatedDate = generatedDate;
  }

  public LocalDateTime getGeneratedDate() {
    return generatedDate;
  }

  public void setRoomId(Long roomId) {
    this.roomId = roomId;
  }

  public Long getRoomId() {
    return roomId;
  }

  public void setRoomNumber(String roomNumber) {
    this.roomNumber = roomNumber;
  }

  public String getRoomNumber() {
    return roomNumber;
  }

  public void setBuildingName(String buildingName) {
    this.buildingName = buildingName;
  }

  public String getBuildingName() {
    return buildingName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantPhone(String tenantPhone) {
    this.tenantPhone = tenantPhone;
  }

  public String getTenantPhone() {
    return tenantPhone;
  }

  public void setDeleted(Integer deleted) {
    this.deleted = deleted;
  }

  public Integer getDeleted() {
    return deleted;
  }
}
