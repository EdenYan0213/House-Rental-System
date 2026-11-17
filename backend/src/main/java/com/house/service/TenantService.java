package com.house.service;

import com.house.model.Contract;
import com.house.model.Tenant;
import com.house.repo.TenantRepository;
import com.house.model.Room;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TenantService {

  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;

  public TenantService(TenantRepository tenantRepository, ContractRepository contractRepository,
      RoomRepository roomRepository) {
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
  }

  public Tenant create(Tenant tenant) {
    if (tenant.getDeleted() == null) {
      tenant.setDeleted(0);
    }
    applyDefaults(tenant);
    return tenantRepository.save(tenant);
  }

  public Optional<Tenant> findById(Long id) {
    return tenantRepository.findById(id).filter(t -> t.getDeleted() == null || t.getDeleted() == 0);
  }

  public List<Tenant> search(String q) {
    List<Tenant> tenants = tenantRepository.findByNameContainingOrPhoneContaining(q, q).stream()
        .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
        .toList();
    if (tenants.isEmpty()) {
      return tenants;
    }
    // 一次性取出所有在租合同，按租户索引，避免每个租户都全表扫描一次
    Map<Long, Contract> activeContractByTenant = contractRepository.findAll().stream()
        .filter(c -> c.getTenantId() != null && "ACTIVE".equals(c.getStatus()))
        .collect(Collectors.toMap(Contract::getTenantId, c -> c, (a, b) -> a));
    for (Tenant tenant : tenants) {
      Contract active = activeContractByTenant.get(tenant.getId());
      tenant.setActiveContract(active != null);
      if (active != null) {
        // Contract.roomNumber 是 @Transient，不会从库里读出来；直接按 roomId 取房间号
        tenant.setActiveRoomNumber(roomRepository.findById(active.getRoomId())
            .map(Room::getRoomNumber)
            .orElse(active.getRoomNumber()));
        tenant.setActiveContractId(active.getId());
      }
    }
    return tenants;
  }

  public Tenant update(Tenant tenant) {
    Optional<Tenant> opt = tenantRepository.findById(tenant.getId());
    if (opt.isPresent()) {
      Tenant existing = opt.get();
      existing.setName(tenant.getName());
      existing.setPhone(tenant.getPhone());
      existing.setIdCard(tenant.getIdCard());
      existing.setEmergencyContact(tenant.getEmergencyContact());
      existing.setEmergencyPhone(tenant.getEmergencyPhone());
      existing.setRemark(tenant.getRemark());
      if (existing.getDeleted() == null) {
        existing.setDeleted(0);
      }
      applyDefaults(existing);
      return tenantRepository.save(existing);
    }
    if (tenant.getDeleted() == null) {
      tenant.setDeleted(0);
    }
    applyDefaults(tenant);
    return tenantRepository.save(tenant);
  }

  private void applyDefaults(Tenant tenant) {
    if (tenant.getLanguagePreference() == null || tenant.getLanguagePreference().isBlank()) {
      tenant.setLanguagePreference("zh-CN");
    }
    if (tenant.getNotifyOverdue() == null) {
      tenant.setNotifyOverdue(1);
    }
    if (tenant.getNotifyRepair() == null) {
      tenant.setNotifyRepair(1);
    }
    if (tenant.getNotifyContract() == null) {
      tenant.setNotifyContract(1);
    }
    if (tenant.getNotifyNotice() == null) {
      tenant.setNotifyNotice(1);
    }
    if (tenant.getRegisterSource() == null || tenant.getRegisterSource().isBlank()) {
      tenant.setRegisterSource("ADMIN");
    }
  }

  public void delete(Long id) {
    tenantRepository.findById(id).ifPresent(tenant -> {
      tenant.setDeleted(1);
      tenantRepository.save(tenant);
    });
  }

  public List<Tenant> getBlacklistedTenants(String q, String reason) {
    List<Tenant> all = tenantRepository.findAll();
    return all.stream()
        .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
        .filter(t -> t.getIsBlacklisted() != null && t.getIsBlacklisted())
        .filter(t -> q == null || q.isBlank() ||
            (t.getName() != null && t.getName().contains(q)) ||
            (t.getPhone() != null && t.getPhone().contains(q)))
        .filter(t -> reason == null || reason.isBlank() ||
            (t.getBlacklistReason() != null && t.getBlacklistReason().contains(reason)))
        .toList();
  }

  public boolean isBlacklisted(Long tenantId) {
    Optional<Tenant> opt = tenantRepository.findById(tenantId);
    return opt
        .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
        .map(t -> t.getIsBlacklisted() != null && t.getIsBlacklisted())
        .orElse(false);
  }

  public String getBlacklistInfo(Long tenantId) {
    Optional<Tenant> opt = tenantRepository.findById(tenantId);
    if (opt.isEmpty() || opt.get().getIsBlacklisted() == null || !opt.get().getIsBlacklisted()) {
      return null;
    }
    Tenant t = opt.get();
    StringBuilder sb = new StringBuilder();
    if (t.getBlacklistReason() != null) {
      sb.append(t.getBlacklistReason());
    }
    if (t.getBlacklistedAt() != null) {
      if (sb.length() > 0)
        sb.append("（");
      sb.append(t.getBlacklistedAt().toLocalDate().toString());
      if (sb.toString().startsWith(t.getBlacklistReason())) {
        sb.append("）");
      } else {
        sb.insert(0, "（").append("）");
      }
    }
    return sb.length() > 0 ? sb.toString() : null;
  }

  @Transactional
  public void addToBlacklist(Long tenantId, String reason, Long adminId) {
    Optional<Tenant> opt = tenantRepository.findById(tenantId);
    if (opt.isEmpty()) {
      throw new IllegalArgumentException("租户不存在");
    }
    Tenant tenant = opt.get();
    if (tenant.getIsBlacklisted() != null && tenant.getIsBlacklisted()) {
      throw new IllegalStateException("该租户已在黑名单中，无需重复加入");
    }
    tenant.setIsBlacklisted(true);
    tenant.setBlacklistReason(reason);
    tenant.setBlacklistedAt(LocalDateTime.now());
    tenant.setBlacklistedBy(adminId);
    tenantRepository.save(tenant);
  }

  @Transactional
  public void removeFromBlacklist(Long tenantId) {
    Optional<Tenant> opt = tenantRepository.findById(tenantId);
    if (opt.isEmpty()) {
      throw new IllegalArgumentException("租户不存在");
    }
    Tenant tenant = opt.get();
    tenant.setIsBlacklisted(false);
    tenant.setBlacklistReason(null);
    tenant.setBlacklistedAt(null);
    tenant.setBlacklistedBy(null);
    tenantRepository.save(tenant);
  }
}
