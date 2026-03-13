package com.house.controller;

import com.house.model.RepairOrder;
import com.house.service.RepairOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repair-orders")
public class RepairOrderController {
  private final RepairOrderService repairOrderService;

  public RepairOrderController(RepairOrderService repairOrderService) {
    this.repairOrderService = repairOrderService;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) Long workerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<RepairOrder> result = repairOrderService.search(status, startDate, endDate, buildingId, workerId, page, size);
    return Map.of(
        "content", result.getContent(),
        "totalElements", result.getTotalElements(),
        "totalPages", result.getTotalPages());
  }

  @GetMapping("/{id}")
  public ResponseEntity<RepairOrder> get(@PathVariable Long id) {
    return repairOrderService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    RepairOrder order = new RepairOrder();
    if (body.get("roomId") != null) {
      order.setRoomId(((Number) body.get("roomId")).longValue());
    }
    if (body.get("tenantId") != null) {
      order.setTenantId(((Number) body.get("tenantId")).longValue());
    }
    if (body.get("faultType") != null) {
      order.setFaultType(body.get("faultType").toString());
    }
    if (body.get("description") != null) {
      order.setDescription(body.get("description").toString());
    }
    if (body.get("applianceId") != null) {
      order.setApplianceId(((Number) body.get("applianceId")).longValue());
    }
    if (body.get("reporterType") != null) {
      order.setReporterType(body.get("reporterType").toString());
    }
    if (body.get("reportedBy") != null) {
      order.setReportedBy(((Number) body.get("reportedBy")).longValue());
    }
    if (body.get("images") instanceof List<?> images) {
      List<String> list = new ArrayList<>();
      for (Object image : images) {
        list.add(image.toString());
      }
      try {
        order.setImages(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list));
      } catch (Exception e) {
        order.setImages("[]");
      }
    }
    return repairOrderService.create(order, request);
  }

  @PutMapping("/{id}/assign")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder assign(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    Long workerId = ((Number) body.get("workerId")).longValue();
    return repairOrderService.assignWorker(id, workerId, request);
  }

  @PutMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder start(@PathVariable Long id, HttpServletRequest request) {
    return repairOrderService.startRepair(id, request);
  }

  @PutMapping("/{id}/complete")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder complete(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String repairResult = body.get("repairResult") == null ? "" : body.get("repairResult").toString();
    Double materialFee = body.get("materialFee") == null ? 0D : ((Number) body.get("materialFee")).doubleValue();
    Double laborFee = body.get("laborFee") == null ? 0D : ((Number) body.get("laborFee")).doubleValue();
    Integer isTenantCharge = body.get("isTenantCharge") == null ? 0 : ((Number) body.get("isTenantCharge")).intValue();
    String feeRemark = body.get("feeRemark") == null ? "" : body.get("feeRemark").toString();
    return repairOrderService.completeRepair(id, repairResult, materialFee, laborFee, isTenantCharge, feeRemark,
        request);
  }

  @PutMapping("/{id}/accept")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder accept(@PathVariable Long id, HttpServletRequest request) {
    return repairOrderService.accept(id, request);
  }

  @PutMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder reject(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    String reason = body.get("reason") == null ? "" : body.get("reason").toString();
    return repairOrderService.reject(id, reason, request);
  }

  @PutMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> cancel(@PathVariable Long id, @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    String reason = body.get("reason") == null ? "" : body.get("reason").toString();
    return ResponseEntity.ok(repairOrderService.cancel(id, reason, request));
  }

  @PutMapping("/{id}/generate-bill")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RepairOrder generateBill(@PathVariable Long id, HttpServletRequest request) {
    return repairOrderService.generateBill(id, request);
  }

  @GetMapping("/room/{roomId}")
  public List<RepairOrder> roomHistory(@PathVariable Long roomId) {
    return repairOrderService.listByRoom(roomId);
  }

  @GetMapping("/tenant/{tenantId}")
  public List<RepairOrder> tenantHistory(@PathVariable Long tenantId) {
    return repairOrderService.listByTenant(tenantId);
  }
}
