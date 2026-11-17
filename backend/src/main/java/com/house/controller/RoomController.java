package com.house.controller;

import com.house.model.Room;
import com.house.model.Contract;
import com.house.model.OperationSnapshot;
import com.house.repo.ContractRepository;
import com.house.service.OperationLogService;
import com.house.service.OperationSnapshotService;
import com.house.service.RoomService;
import com.house.dto.BatchRoomRequest;
import com.house.dto.BatchCreateResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;
  private final ContractRepository contractRepository;
  private final OperationLogService operationLogService;
  private final OperationSnapshotService operationSnapshotService;

  public RoomController(RoomService roomService, ContractRepository contractRepository,
      OperationLogService operationLogService,
      OperationSnapshotService operationSnapshotService) {
    this.roomService = roomService;
    this.contractRepository = contractRepository;
    this.operationLogService = operationLogService;
    this.operationSnapshotService = operationSnapshotService;
  }

  @PostMapping("/batch")
  public ResponseEntity<?> batchCreate(@RequestBody BatchRoomRequest req) {
    try {
      BatchCreateResult result = roomService.createBatch(req);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/batch/override")
  public ResponseEntity<?> batchCreateWithOverride(@RequestBody BatchRoomRequest req) {
    try {
      BatchCreateResult result = roomService.createBatchWithOverride(req);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PostMapping("/batch-adjust-rent")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> batchAdjustRent(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    try {
      List<?> roomIdRaw = (List<?>) body.get("roomIds");
      if (roomIdRaw == null || roomIdRaw.isEmpty()) {
        return ResponseEntity.badRequest().body("请先选择房间");
      }
      List<Long> roomIds = roomIdRaw.stream().map(v -> ((Number) v).longValue()).toList();
      String mode = body.get("mode") == null ? "FIXED" : body.get("mode").toString();
      Double fixedAmount = body.get("fixedAmount") == null ? null : ((Number) body.get("fixedAmount")).doubleValue();
      Double percent = body.get("percent") == null ? null : ((Number) body.get("percent")).doubleValue();
      boolean syncActiveContracts = body.get("syncActiveContracts") == null
          || Boolean.TRUE.equals(body.get("syncActiveContracts"));

      List<Room> rooms = roomService.listByIds(roomIds);
      List<Map<String, Object>> preview = new ArrayList<>();
      int affectedContracts = 0;

      for (Room room : rooms) {
        List<Contract> activeContracts = contractRepository.findByRoomIdAndStatus(room.getId(), "ACTIVE");
        Double oldRent = activeContracts.isEmpty() ? 0D
            : (activeContracts.get(0).getMonthlyRent() == null ? 0D : activeContracts.get(0).getMonthlyRent());
        Double newRent;
        if ("PERCENT".equalsIgnoreCase(mode)) {
          if (percent == null) {
            return ResponseEntity.badRequest().body("百分比不能为空");
          }
          newRent = oldRent * (1 + percent / 100D);
        } else {
          if (fixedAmount == null) {
            return ResponseEntity.badRequest().body("调整金额不能为空");
          }
          newRent = oldRent + fixedAmount;
        }
        if (newRent < 0) {
          newRent = 0D;
        }

        if (syncActiveContracts) {
          for (Contract c : activeContracts) {
            c.setMonthlyRent(newRent);
            contractRepository.save(c);
            affectedContracts++;
          }
        }

        Map<String, Object> row = new HashMap<>();
        row.put("roomId", room.getId());
        row.put("roomNumber", room.getRoomNumber());
        row.put("oldRent", oldRent);
        row.put("newRent", newRent);
        row.put("activeContractCount", activeContracts.size());
        preview.add(row);
      }

      operationLogService.log("批量操作", "批量调整租金: 房间" + rooms.size() + "个, 同步合同" + affectedContracts + "份", request);
      return ResponseEntity.ok(Map.of(
          "affectedRooms", rooms.size(),
          "affectedContracts", affectedContracts,
          "preview", preview));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("批量调整租金失败: " + e.getMessage());
    }
  }

  @GetMapping
  public List<Room> list(@RequestParam(required = false) Long buildingId, @RequestParam(required = false) Integer floor,
      @RequestParam(required = false) String status) {
    // 三个筛选条件都可选，统一交给 service 处理（含状态归一化）
    return roomService.listByBuildingAndFloor(buildingId, floor, status);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Room> get(@PathVariable Long id) {
    return roomService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public Room create(@RequestBody Room room) {
    return roomService.create(room);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Room> update(@PathVariable Long id, @RequestBody Room room) {
    return roomService.findById(id).map(existing -> {
      room.setId(id);
      Room saved = roomService.update(room);
      return ResponseEntity.ok(saved);
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    Room before = roomService.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "房屋管理", "delete", "room", id, before.getRoomNumber(), before);
    roomService.delete(id);
    return ResponseEntity.ok(Map.of(
        "undoSnapshotId", snapshot.getId(),
        "undoExpireAt", snapshot.getExpiredAt(),
        "undoTip", "操作成功，可在30分钟内撤销"));
  }
}
