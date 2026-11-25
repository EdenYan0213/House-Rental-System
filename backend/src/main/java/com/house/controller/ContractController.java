package com.house.controller;

import com.house.dto.ContractCreateRequest;
import com.house.model.Contract;
import com.house.model.OperationSnapshot;
import com.house.model.Room;
import com.house.repo.ContractRepository;
import com.house.repo.RoomRepository;
import com.house.service.ContractService;
import com.house.service.OperationLogService;
import com.house.dto.MoveOutRequest;
import com.house.service.MoveOutService;
import com.house.service.OperationSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

  private final ContractService contractService;
  private final OperationLogService operationLogService;
  private final ContractRepository contractRepository;
  private final RoomRepository roomRepository;
  private final OperationSnapshotService operationSnapshotService;
  private final MoveOutService moveOutService;

  public ContractController(ContractService contractService, OperationLogService operationLogService,
      ContractRepository contractRepository, RoomRepository roomRepository,
      OperationSnapshotService operationSnapshotService,
      MoveOutService moveOutService) {
    this.contractService = contractService;
    this.operationLogService = operationLogService;
    this.contractRepository = contractRepository;
    this.roomRepository = roomRepository;
    this.operationSnapshotService = operationSnapshotService;
    this.moveOutService = moveOutService;
  }

  /**
   * 合同列表。
   *
   * <p>传 {@code paged=true} 返回 {@code {content, totalElements, totalPages, page, size}}，
   * 供需要翻页的前端使用；不传则保持旧行为（纯数组），兼容现有调用方。
   */
  @GetMapping
  public Object list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String expiring,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean paged) {
    if (paged) {
      return contractService.searchPaged(status, q, page, size, expiring);
    }
    return contractService.search(status, q, page, size, expiring);
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody ContractCreateRequest req) {
    try {
      Contract c = contractService.createContract(req);
      return ResponseEntity.ok(c);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).body(e.getMessage());
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<Contract> get(@PathVariable Long id) {
    return contractService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}")
  public ResponseEntity<Contract> update(@PathVariable Long id, @RequestBody Contract contract,
      HttpServletRequest request) {
    return contractService.findById(id).map(existing -> {
      Double beforeRent = existing.getMonthlyRent();
      contract.setId(id);
      Contract saved = contractService.update(contract);
      if (beforeRent != null && saved.getMonthlyRent() != null && !beforeRent.equals(saved.getMonthlyRent())) {
        operationLogService.log(
            "contract", "edit", String.valueOf(id), existing.getContractNo(), existing, saved,
            "修改租金: " + beforeRent + " -> " + saved.getMonthlyRent(), request, true, null, null,
            Map.of("contractId", id, "beforeRent", beforeRent, "afterRent", saved.getMonthlyRent()));
      }
      return ResponseEntity.ok(saved);
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
    Contract before = contractRepository.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "合同管理", "delete", "contract", id, before.getContractNo(), before);
    contractService.delete(id);
    operationLogService.log(
        "contract", "delete", String.valueOf(id), before.getContractNo(), before, null,
        "删除合同: " + before.getContractNo(), request, true, null, null, Map.of("contractId", id));
    return ResponseEntity.ok(UndoResponse.of(snapshot));
  }

  /**
   * 退租预检：返回押金余额、未结清账单、缺读数等关键信息，供退租向导展示。
   */
  @GetMapping("/{id}/move-out-check")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> moveOutCheck(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(moveOutService.check(id));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  /**
   * 办理退租：退押金 + 解约 + 房间置空闲 + 可选加黑名单，一次事务完成。
   */
  @PostMapping("/{id}/move-out")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> moveOut(@PathVariable Long id, @RequestBody MoveOutRequest req,
      HttpServletRequest request) {
    try {
      Contract beforeContract = contractRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("合同不存在"));
      Room beforeRoom = beforeContract.getRoomId() == null ? null
          : roomRepository.findById(beforeContract.getRoomId()).orElse(null);
      Map<String, Object> beforePayload = new HashMap<>();
      beforePayload.put("contract", beforeContract);
      beforePayload.put("room", beforeRoom);
      OperationSnapshot snapshot = operationSnapshotService.capture(
          "合同管理", "move_out", "contract", id, beforeContract.getContractNo(), beforePayload);

      Map<String, Object> result = new HashMap<>(moveOutService.settle(id, req));

      Contract afterContract = contractRepository.findById(id).orElse(null);
      Room afterRoom = (afterContract == null || afterContract.getRoomId() == null) ? null
          : roomRepository.findById(afterContract.getRoomId()).orElse(null);
      Map<String, Object> afterPayload = new HashMap<>();
      afterPayload.put("contract", afterContract);
      afterPayload.put("room", afterRoom);
      operationSnapshotService.complete(snapshot.getId(), afterPayload);

      operationLogService.log("合同管理", "办理退租: " + beforeContract.getContractNo(), request);

      result.putAll(UndoResponse.of(snapshot, "退租完成，可在30分钟内撤销"));
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PostMapping("/{id}/terminate")
  public ResponseEntity<?> terminate(@PathVariable Long id, @RequestBody java.util.Map<String, String> body,
      HttpServletRequest request) {
    try {
      Contract beforeContract = contractRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("合同不存在"));
      Room beforeRoom = beforeContract.getRoomId() == null ? null
          : roomRepository.findById(beforeContract.getRoomId()).orElse(null);
      Map<String, Object> beforePayload = new HashMap<>();
      beforePayload.put("contract", beforeContract);
      beforePayload.put("room", beforeRoom);
      OperationSnapshot snapshot = operationSnapshotService.capture(
          "合同管理", "terminate", "contract", id, beforeContract.getContractNo(), beforePayload);
      String terminateDate = body.get("terminateDate");
      String reason = body.get("reason");
      contractService.terminate(id, terminateDate, reason);
      Contract afterContract = contractRepository.findById(id).orElse(null);
      Room afterRoom = (afterContract == null || afterContract.getRoomId() == null) ? null
          : roomRepository.findById(afterContract.getRoomId()).orElse(null);
      Map<String, Object> afterPayload = new HashMap<>();
      afterPayload.put("contract", afterContract);
      afterPayload.put("room", afterRoom);
      operationSnapshotService.complete(snapshot.getId(), afterPayload);
      return ResponseEntity.ok(UndoResponse.of(snapshot));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/{id}/renew")
  public ResponseEntity<?> renew(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
    try {
      Contract renewed = contractService.renew(id, (String) body.get("endDate"));
      return ResponseEntity.ok(renewed);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/batch-renew")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchRenew(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    try {
      List<?> idsRaw = (List<?>) body.get("contractIds");
      if (idsRaw == null || idsRaw.isEmpty()) {
        return ResponseEntity.badRequest().body("请选择合同");
      }
      List<Long> contractIds = idsRaw.stream().map(v -> ((Number) v).longValue()).toList();
      Map<String, Object> result = contractService.batchRenew(
          contractIds,
          body.get("startDate") == null ? null : body.get("startDate").toString(),
          body.get("endDate") == null ? null : body.get("endDate").toString(),
          body.get("durationMonths") == null ? null : ((Number) body.get("durationMonths")).intValue(),
          body.get("rentMode") == null ? null : body.get("rentMode").toString(),
          body.get("fixedAmount") == null ? null : ((Number) body.get("fixedAmount")).doubleValue(),
          body.get("percent") == null ? null : ((Number) body.get("percent")).doubleValue(),
          body.get("inheritDeposit") == null ? Boolean.TRUE : Boolean.valueOf(body.get("inheritDeposit").toString()),
          body.get("inheritPaymentMethod") == null ? Boolean.TRUE
              : Boolean.valueOf(body.get("inheritPaymentMethod").toString()));
      operationLogService.log("contract", "edit", null, null, null, null,
          "批量续租: 合同" + contractIds.size() + "份", request, true, null, null, body);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/batch-terminate")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchTerminate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    try {
      List<?> idsRaw = (List<?>) body.get("contractIds");
      if (idsRaw == null || idsRaw.isEmpty()) {
        return ResponseEntity.badRequest().body("请选择合同");
      }
      List<Long> contractIds = idsRaw.stream().map(v -> ((Number) v).longValue()).toList();
      Map<String, Object> result = contractService.batchTerminate(
          contractIds,
          body.get("terminateDate") == null ? null : body.get("terminateDate").toString(),
          body.get("reason") == null ? null : body.get("reason").toString(),
          body.get("allowForce") != null && Boolean.parseBoolean(body.get("allowForce").toString()));
      operationLogService.log("contract", "edit", null, null, null, null,
          "批量退租: 合同" + contractIds.size() + "份", request, true, null, null, body);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
