package com.house.controller;

import com.house.model.ComplaintSuggestion;
import com.house.model.Handler;
import com.house.service.ComplaintService;
import com.house.util.RequestValues;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {
  private final ComplaintService complaintService;

  @Value("${app.complaint.require-confirm:true}")
  private boolean requireConfirmByDefault;

  public ComplaintController(ComplaintService complaintService) {
    this.complaintService = complaintService;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return complaintService.search(status, type, priority, startDate, endDate, buildingId, q, page, size);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ComplaintSuggestion> get(@PathVariable Long id) {
    return complaintService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    return complaintService.create(body, request);
  }

  @PutMapping("/{id}/accept")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion accept(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String note = body.get("note") == null ? "" : body.get("note").toString();
    Long assignedTo = RequestValues.parseLong(body.get("assignedTo"));
    return complaintService.accept(id, note, assignedTo, request);
  }

  @PutMapping("/{id}/reply")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion reply(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String content = body.get("content") == null ? "" : body.get("content").toString();
    return complaintService.reply(id, content, request);
  }

  @PutMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion complete(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String result = body.get("result") == null ? "" : body.get("result").toString();
    boolean requireConfirm = requireConfirmByDefault;
    if (body.get("requireConfirm") != null) {
      requireConfirm = Boolean.parseBoolean(body.get("requireConfirm").toString());
    }
    return complaintService.complete(id, result, requireConfirm, request);
  }

  @PutMapping("/{id}/reassign")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion reassign(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    Long assignedTo = RequestValues.parseLong(body.get("assignedTo"));
    String note = body.get("note") == null ? "" : body.get("note").toString();
    return complaintService.reassign(id, assignedTo, note, request);
  }

  @PutMapping("/{id}/close")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion close(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String reason = body.get("reason") == null ? "" : body.get("reason").toString();
    return complaintService.close(id, reason, request);
  }

  @PutMapping("/{id}/confirm")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ComplaintSuggestion confirm(@PathVariable Long id, HttpServletRequest request) {
    return complaintService.confirmComplete(id, request);
  }

  @GetMapping("/tenant/{tenantId}")
  public List<ComplaintSuggestion> tenantHistory(@PathVariable Long tenantId) {
    return complaintService.listByTenant(tenantId);
  }

  @GetMapping("/handlers")
  public List<Handler> handlers() {
    return complaintService.listEnabledHandlers();
  }

  @GetMapping("/stats")
  public Map<String, Object> stats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) Long buildingId) {
    return complaintService.stats(startDate, endDate, buildingId);
  }

}
