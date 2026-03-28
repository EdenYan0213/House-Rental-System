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

  @Column(name = "password")
  private String password;

  @Convert(converter = CryptoConverter.class)
  private String idCard; // 敏感信息加密存储

  @Column(name = "emergency_contact")
  private String emergencyContact;

  @Column(name = "emergency_phone")
  private String emergencyPhone;

  private String remark;

  @Column(name = "register_source")
  private String registerSource;

  @Column(name = "is_blacklisted")
  private Boolean isBlacklisted;

  @Column(name = "blacklist_reason")
  private String blacklistReason;

  @Column(name = "blacklisted_at")
  private java.time.LocalDateTime blacklistedAt;

  @Column(name = "blacklisted_by")
  private Long blacklistedBy;

  @Column(name = "language_preference")
  private String languagePreference;

  @Column(name = "notify_overdue")
  private Integer notifyOverdue;

  @Column(name = "notify_repair")
  private Integer notifyRepair;

  @Column(name = "notify_contract")
  private Integer notifyContract;

  @Column(name = "notify_notice")
  private Integer notifyNotice;

  private Integer deleted;

  @Transient
  private Boolean activeContract;

  public void setId(Long id) {
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getPhone() {
    return phone;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getPassword() {
    return password;
  }

  public void setIdCard(String idCard) {
    this.idCard = idCard;
  }

  public String getIdCard() {
    return idCard;
  }

  public void setEmergencyContact(String emergencyContact) {
    this.emergencyContact = emergencyContact;
  }

  public String getEmergencyContact() {
    return emergencyContact;
  }

  public void setEmergencyPhone(String emergencyPhone) {
    this.emergencyPhone = emergencyPhone;
  }

  public String getEmergencyPhone() {
    return emergencyPhone;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public String getRemark() {
    return remark;
  }

  public void setRegisterSource(String registerSource) {
    this.registerSource = registerSource;
  }

  public String getRegisterSource() {
    return registerSource;
  }

  public void setIsBlacklisted(Boolean isBlacklisted) {
    this.isBlacklisted = isBlacklisted;
  }

  public Boolean getIsBlacklisted() {
    return isBlacklisted;
  }

  public void setBlacklistReason(String blacklistReason) {
    this.blacklistReason = blacklistReason;
  }

  public String getBlacklistReason() {
    return blacklistReason;
  }

  public void setBlacklistedAt(java.time.LocalDateTime blacklistedAt) {
    this.blacklistedAt = blacklistedAt;
  }

  public java.time.LocalDateTime getBlacklistedAt() {
    return blacklistedAt;
  }

  public void setBlacklistedBy(Long blacklistedBy) {
    this.blacklistedBy = blacklistedBy;
  }

  public Long getBlacklistedBy() {
    return blacklistedBy;
  }

  public void setLanguagePreference(String languagePreference) {
    this.languagePreference = languagePreference;
  }

  public String getLanguagePreference() {
    return languagePreference;
  }

  public void setNotifyOverdue(Integer notifyOverdue) {
    this.notifyOverdue = notifyOverdue;
  }

  public Integer getNotifyOverdue() {
    return notifyOverdue;
  }

  public void setNotifyRepair(Integer notifyRepair) {
    this.notifyRepair = notifyRepair;
  }

  public Integer getNotifyRepair() {
    return notifyRepair;
  }

  public void setNotifyContract(Integer notifyContract) {
    this.notifyContract = notifyContract;
  }

  public Integer getNotifyContract() {
    return notifyContract;
  }

  public void setNotifyNotice(Integer notifyNotice) {
    this.notifyNotice = notifyNotice;
  }

  public Integer getNotifyNotice() {
    return notifyNotice;
  }

  public void setDeleted(Integer deleted) {
    this.deleted = deleted;
  }

  public Integer getDeleted() {
    return deleted;
  }

  public Boolean getActiveContract() {
    return activeContract;
  }

  public void setActiveContract(Boolean activeContract) {
    this.activeContract = activeContract;
  }
}
