package com.house.controller;

import com.house.model.Tenant;
import com.house.model.OperationSnapshot;
import com.house.repo.TenantRepository;
import com.house.service.OperationLogService;
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
  private final OperationLogService operationLogService;

  public TenantController(TenantService tenantService, TenantRepository tenantRepository,
      OperationSnapshotService operationSnapshotService,
      OperationLogService operationLogService) {
    this.tenantService = tenantService;
    this.tenantRepository = tenantRepository;
    this.operationSnapshotService = operationSnapshotService;
    this.operationLogService = operationLogService;
  }

  /**
   * 租户列表。
   *
   * <p>传 {@code paged=true} 返回分页结构，否则返回纯数组（兼容旧调用）。
   * 租户表数据量大，前端列表应优先使用分页模式。
   */
  @GetMapping
  public Object list(@RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean paged) {
    List<Tenant> all = (q == null || q.isBlank())
        ? tenantService.search("")
        : tenantService.search(q);

    if (!paged) {
      return all;
    }
    int total = all.size();
    int safeSize = Math.max(size, 1);
    int safePage = Math.max(page, 0);
    int from = Math.min(safePage * safeSize, total);
    int to = Math.min(from + safeSize, total);

    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("content", all.subList(from, to));
    body.put("totalElements", total);
    body.put("totalPages", (int) Math.ceil((double) total / safeSize));
    body.put("page", safePage);
    body.put("size", safeSize);
    return body;
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
      return ResponseEntity.ok(UndoResponse.withData("data", saved, snapshot));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
    Tenant before = tenantRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "租户管理", "soft_delete", "tenant", id, before.getName(), before);
    tenantService.delete(id);
    Tenant after = tenantRepository.findById(id).orElse(null);
    operationSnapshotService.complete(snapshot.getId(), after);
    operationLogService.log(
        "tenant", "delete", String.valueOf(id), before.getName(), before, after,
        "删除租户: " + before.getName(), request, true, null, null, Map.of("tenantId", id));
    return ResponseEntity.ok(UndoResponse.of(snapshot));
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
      operationLogService.log(
          "tenant", "remove_blacklist", String.valueOf(id), before.getName(), before, after,
          "移出黑名单: " + before.getName(), request, true, null, null, Map.of("tenantId", id));
      return ResponseEntity.ok(UndoResponse.of(snapshot));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
