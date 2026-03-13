package com.house.controller;

import com.house.model.OperationSnapshot;
import com.house.service.OperationSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-snapshots")
public class OperationSnapshotController {
  private final OperationSnapshotService operationSnapshotService;

  public OperationSnapshotController(OperationSnapshotService operationSnapshotService) {
    this.operationSnapshotService = operationSnapshotService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> list(
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var resultPage = operationSnapshotService.search(username, module, keyword, startDate, endDate, page, size);
    Map<String, Object> result = new HashMap<>();
    result.put("content", resultPage.getContent());
    result.put("totalElements", resultPage.getTotalElements());
    result.put("totalPages", resultPage.getTotalPages());
    return result;
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> detail(@PathVariable Long id) {
    try {
      OperationSnapshot snapshot = operationSnapshotService.getOrThrow(id);
      Map<String, Object> result = new HashMap<>();
      result.put("snapshot", snapshot);
      result.put("beforeData", operationSnapshotService.parseJson(snapshot.getBeforeData()));
      result.put("afterData",
          snapshot.getAfterData() == null ? null : operationSnapshotService.parseJson(snapshot.getAfterData()));
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/recover")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> recover(@PathVariable Long id, HttpServletRequest request) {
    try {
      return ResponseEntity.ok(operationSnapshotService.recover(id, request));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
