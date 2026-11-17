package com.house.service;

import com.house.model.Tenant;
import com.house.model.Contract;
import com.house.repo.TenantRepository;
import com.house.repo.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TenantService {

  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;

  public TenantService(TenantRepository tenantRepository, ContractRepository contractRepository) {
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
  }

  public Tenant create(Tenant tenant) {
    if (tenant.getDeleted() == null) {
      tenant.setDeleted(0);
    }
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
    // 一次性取出在租合同的租户ID，避免每个租户都全表扫描一次
    Set<Long> tenantIdsWithActiveContract = contractRepository.findAll().stream()
        .filter(c -> c.getTenantId() != null && "ACTIVE".equals(c.getStatus()))
        .map(Contract::getTenantId)
        .collect(Collectors.toSet());
    for (Tenant tenant : tenants) {
      tenant.setActiveContract(tenantIdsWithActiveContract.contains(tenant.getId()));
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
      return tenantRepository.save(existing);
    }
    if (tenant.getDeleted() == null) {
      tenant.setDeleted(0);
    }
    return tenantRepository.save(tenant);
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
