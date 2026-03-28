package com.house.controller;

import com.house.model.AlertRecord;
import com.house.model.AlertRule;
import com.house.service.AlertService;
import com.house.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
  private final AlertService alertService;
  private final UserService userService;

  public AlertController(AlertService alertService, UserService userService) {
    this.alertService = alertService;
    this.userService = userService;
  }

  @GetMapping("/rules")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public List<AlertRule> listRules() {
    return alertService.listRules();
  }

  @PostMapping("/rules")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public AlertRule saveRule(@RequestBody AlertRule rule) {
    return alertService.saveRule(rule);
  }

  @DeleteMapping("/rules/{id}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<?> deleteRule(@PathVariable Integer id) {
    alertService.deleteRule(id);
    return ResponseEntity.ok(Map.of("message", "删除成功"));
  }

  @GetMapping("/records")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, Object> listRecords(
      @RequestParam(required = false) String alertType,
      @RequestParam(required = false) Integer status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<AlertRecord> result = alertService.searchRecords(alertType, status, page, size);
    return Map.of(
        "content", result.getContent(),
        "totalElements", result.getTotalElements(),
        "totalPages", result.getTotalPages());
  }

  @PutMapping("/records/{id}/handle")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public AlertRecord handleRecord(@PathVariable Integer id, @AuthenticationPrincipal UserDetails userDetails) {
    Integer operatorId = null;
    if (userDetails != null) {
      var user = userService.findByUsername(userDetails.getUsername());
      operatorId = user == null ? null : Math.toIntExact(user.getId());
    }
    return alertService.markHandled(id, operatorId);
  }
}
