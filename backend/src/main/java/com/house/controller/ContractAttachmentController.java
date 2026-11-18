package com.house.controller;

import com.house.model.ContractAttachment;
import com.house.service.ContractAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 合同附件版本管理。
 *
 * <p>合同正文/证件每替换一次就保留一个版本，可追溯历史。
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/attachments")
@RequiredArgsConstructor
public class ContractAttachmentController {

  private final ContractAttachmentService attachmentService;

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public List<ContractAttachment> list(@PathVariable Long contractId) {
    return attachmentService.list(contractId);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> add(@PathVariable Long contractId, @RequestBody Map<String, Object> body) {
    try {
      String fileUrl = body.get("fileUrl") == null ? null : body.get("fileUrl").toString();
      String fileName = body.get("fileName") == null ? null : body.get("fileName").toString();
      String category = body.get("category") == null ? "CONTRACT" : body.get("category").toString();
      String remark = body.get("remark") == null ? null : body.get("remark").toString();
      Long fileSize = body.get("fileSize") instanceof Number n ? n.longValue() : null;

      return ResponseEntity.ok(
          attachmentService.addVersion(contractId, fileUrl, fileName, fileSize, category, remark));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @DeleteMapping("/{attachmentId}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> delete(@PathVariable Long contractId, @PathVariable Long attachmentId) {
    try {
      attachmentService.deleteVersion(contractId, attachmentId);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }
}
