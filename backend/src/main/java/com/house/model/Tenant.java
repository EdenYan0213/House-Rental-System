package com.house.model;

import com.house.util.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;
  private String phone;

  @Convert(converter = CryptoConverter.class)
  private String idCard; // 敏感信息加密存储

  @Column(name = "emergency_contact")
  private String emergencyContact;

  @Column(name = "emergency_phone")
  private String emergencyPhone;

  private String remark;

  @Column(name = "is_blacklisted")
  private Boolean isBlacklisted;

  @Column(name = "blacklist_reason")
  private String blacklistReason;

  @Column(name = "blacklisted_at")
  private java.time.LocalDateTime blacklistedAt;

  @Column(name = "blacklisted_by")
  private Long blacklistedBy;

  private Integer deleted;

  @Transient
  private Boolean activeContract;

  public void setId(Long id) { this.id = id; }
  public Long getId() { return id; }
  public void setName(String name) { this.name = name; }
  public String getName() { return name; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getPhone() { return phone; }
  public void setIdCard(String idCard) { this.idCard = idCard; }
  public String getIdCard() { return idCard; }
  public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
  public String getEmergencyContact() { return emergencyContact; }
  public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
  public String getEmergencyPhone() { return emergencyPhone; }
  public void setRemark(String remark) { this.remark = remark; }
  public String getRemark() { return remark; }
  public void setIsBlacklisted(Boolean isBlacklisted) { this.isBlacklisted = isBlacklisted; }
  public Boolean getIsBlacklisted() { return isBlacklisted; }
  public void setBlacklistReason(String blacklistReason) { this.blacklistReason = blacklistReason; }
  public String getBlacklistReason() { return blacklistReason; }
  public void setBlacklistedAt(java.time.LocalDateTime blacklistedAt) { this.blacklistedAt = blacklistedAt; }
  public java.time.LocalDateTime getBlacklistedAt() { return blacklistedAt; }
  public void setBlacklistedBy(Long blacklistedBy) { this.blacklistedBy = blacklistedBy; }
  public Long getBlacklistedBy() { return blacklistedBy; }
  public void setDeleted(Integer deleted) { this.deleted = deleted; }
  public Integer getDeleted() { return deleted; }

  @PrePersist
  void prePersist() {
    if (deleted == null) {
      deleted = 0;
    }
    if (isBlacklisted == null) {
      isBlacklisted = Boolean.FALSE;
    }
  }
  public Boolean getActiveContract() { return activeContract; }
  public void setActiveContract(Boolean activeContract) { this.activeContract = activeContract; }
}
