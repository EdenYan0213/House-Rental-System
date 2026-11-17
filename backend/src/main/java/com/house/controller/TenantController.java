package com.house.controller;

import com.house.model.Tenant;
import com.house.model.OperationSnapshot;
import com.house.repo.TenantRepository;
import com.house.service.OperationSnapshotService;
import com.house.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {
  private final TenantService tenantService;
  private final TenantRepository tenantRepository;
  private final OperationSnapshotService operationSnapshotService;

  public TenantController(TenantService tenantService, TenantRepository tenantRepository,
      OperationSnapshotService operationSnapshotService) {
    this.tenantService = tenantService;
    this.tenantRepository = tenantRepository;
    this.operationSnapshotService = operationSnapshotService;
  }

  @GetMapping
  public List<Tenant> list(@RequestParam(required = false) String q) {
    if (q == null || q.isBlank())
      return tenantService.search("");
    return tenantService.search(q);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Tenant> get(@PathVariable Long id) {
    return tenantService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public Tenant create(@RequestBody Tenant tenant) {
    return tenantService.create(tenant);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Tenant tenant) {
    return tenantService.findById(id).map(existing -> {
      OperationSnapshot snapshot = operationSnapshotService.capture(
          "租户管理", "edit", "tenant", id, existing.getName(), existing);
      tenant.setId(id);
      Tenant saved = tenantService.update(tenant);
      operationSnapshotService.complete(snapshot.getId(), saved);
      return ResponseEntity.ok(Map.of(
          "data", saved,
          "undoSnapshotId", snapshot.getId(),
          "undoExpireAt", snapshot.getExpiredAt(),
          "undoTip", "操作成功，可在30分钟内撤销"));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    Tenant before = tenantRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "租户管理", "soft_delete", "tenant", id, before.getName(), before);
    tenantService.delete(id);
    Tenant after = tenantRepository.findById(id).orElse(null);
    operationSnapshotService.complete(snapshot.getId(), after);
    return ResponseEntity.ok(Map.of(
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "操作成功，可在30分钟内撤销"));
  }

  @GetMapping("/blacklist")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public List<Tenant> blacklist(@RequestParam(required = false) String q,
      @RequestParam(required = false) String reason) {
    return tenantService.getBlacklistedTenants(q, reason);
  }

  // 只读查询：签约向导中对普通用户也要展示黑名单警告，故仅要求登录
  @GetMapping("/{id}/blacklist-check")
  public Map<String, Object> checkBlacklist(@PathVariable Long id) {
    boolean blacklisted = tenantService.isBlacklisted(id);
    String info = tenantService.getBlacklistInfo(id);
    return Map.of("blacklisted", blacklisted, "info", info);
  }

  @PostMapping("/{id}/blacklist")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> addToBlacklist(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    try {
      String reason = (String) body.get("reason");
      Object adminIdObj = body.get("adminId");
      Long adminId = 1L;
      if (adminIdObj != null) {
        if (adminIdObj instanceof Number) {
          adminId = ((Number) adminIdObj).longValue();
        } else {
          adminId = Long.parseLong(adminIdObj.toString());
        }
      }
      tenantService.addToBlacklist(id, reason, adminId);
      return ResponseEntity.ok().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}/blacklist")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> removeFromBlacklist(@PathVariable Long id, HttpServletRequest request) {
    try {
      Tenant before = tenantRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("租户不存在"));
      OperationSnapshot snapshot = operationSnapshotService.capture(
          "黑名单管理", "remove_blacklist", "tenant", id, before.getName(), before);
      tenantService.removeFromBlacklist(id);
      Tenant after = tenantRepository.findById(id).orElse(null);
      operationSnapshotService.complete(snapshot.getId(), after);
      return ResponseEntity.ok(Map.of(
          "undoSnapshotId", snapshot.getId(),
          "undoExpireAt", snapshot.getExpiredAt(),
          "undoTip", "操作成功，可在30分钟内撤销"));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
