package com.house.controller;

import com.house.model.Room;
import com.house.model.Contract;
import com.house.model.Building;
import com.house.model.FeeItem;
import com.house.model.Tenant;
import com.house.model.RoomAppliance;
import com.house.model.ContractStatus;
import com.house.model.RoomStatus;
import com.house.model.OperationSnapshot;
import com.house.repo.BuildingRepository;
import com.house.repo.ContractRepository;
import com.house.repo.FeeItemRepository;
import com.house.repo.RoomApplianceRepository;
import com.house.repo.TenantRepository;
import com.house.service.OperationLogService;
import com.house.service.OperationSnapshotService;
import com.house.service.RoomService;
import com.house.dto.BatchRoomRequest;
import com.house.dto.BatchCreateResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;
  private final ContractRepository contractRepository;
  private final BuildingRepository buildingRepository;
  private final RoomApplianceRepository roomApplianceRepository;
  private final FeeItemRepository feeItemRepository;
  private final TenantRepository tenantRepository;
  private final OperationLogService operationLogService;
  private final OperationSnapshotService operationSnapshotService;

  public RoomController(RoomService roomService, ContractRepository contractRepository,
      BuildingRepository buildingRepository,
      RoomApplianceRepository roomApplianceRepository,
      FeeItemRepository feeItemRepository,
      TenantRepository tenantRepository,
      OperationLogService operationLogService,
      OperationSnapshotService operationSnapshotService) {
    this.roomService = roomService;
    this.contractRepository = contractRepository;
    this.buildingRepository = buildingRepository;
    this.roomApplianceRepository = roomApplianceRepository;
    this.feeItemRepository = feeItemRepository;
    this.tenantRepository = tenantRepository;
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
        List<Contract> activeContracts = contractRepository.findByRoomIdAndStatusIn(
            room.getId(), ContractStatus.ACTIVE_RAW_VALUES);
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

      operationLogService.log("contract", "edit", null, null, null, preview,
          "批量调整租金: 房间" + rooms.size() + "个, 同步合同" + affectedContracts + "份",
          request, true, null, null, body);
      return ResponseEntity.ok(Map.of(
          "affectedRooms", rooms.size(),
          "affectedContracts", affectedContracts,
          "preview", preview));
    } catch (Exception e) {
      return ResponseEntity.badRequest().body("批量调整租金失败: " + e.getMessage());
    }
  }

  /**
   * 房间列表。
   *
   * <p>向后兼容：不传 page 时返回纯数组（现有前端与二维码批量导出依赖该形状）；
   * 传了 page 才返回 { content, totalElements, page, size } 分页对象。
   */
  @GetMapping
  public Object list(@RequestParam(required = false) Long buildingId,
      @RequestParam(required = false) Integer floor,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "50") int size) {
    List<Room> rooms = roomService.listByBuildingAndFloor(buildingId, floor, status);
    if (page == null) {
      return rooms;
    }
    int total = rooms.size();
    int safeSize = size <= 0 ? 50 : size;
    int from = Math.max(page, 0) * safeSize;
    int to = Math.min(total, from + safeSize);
    List<Room> content = from >= total ? List.of() : rooms.subList(from, to);

    Map<String, Object> result = new HashMap<>();
    result.put("content", content);
    result.put("totalElements", total);
    result.put("page", page);
    result.put("size", safeSize);
    return result;
  }

  @GetMapping("/{id}")
  public ResponseEntity<Room> get(@PathVariable Long id) {
    return roomService.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/detail")
  public ResponseEntity<?> getDetail(@PathVariable Long id) {
    Room room = roomService.findById(id).orElse(null);
    if (room == null) {
      return ResponseEntity.notFound().build();
    }

    Building building = null;
    if (room.getBuildingId() != null) {
      building = buildingRepository.findById(room.getBuildingId()).orElse(null);
    }

    List<Contract> activeContracts = contractRepository.findByRoomIdAndStatusIn(
        room.getId(), ContractStatus.ACTIVE_RAW_VALUES);

    Contract activeContract = (activeContracts == null || activeContracts.isEmpty()) ? null : activeContracts.get(0);
    Tenant tenant = null;
    if (activeContract != null && activeContract.getTenantId() != null) {
      tenant = tenantRepository.findById(activeContract.getTenantId()).orElse(null);
    }

    List<RoomAppliance> appliances = roomApplianceRepository.findByRoomIdOrderByIdDesc(room.getId());
    List<FeeItem> feeItems = feeItemRepository.findAll();

    Double waterUnitPrice = building != null ? building.getWaterUnitPrice() : null;
    Double electricUnitPrice = building != null ? building.getElectricUnitPrice() : null;

    if (waterUnitPrice == null) {
      FeeItem water = feeItems.stream().filter(f -> f.getName() != null && f.getName().contains("水")).findFirst().orElse(null);
      waterUnitPrice = water == null ? null : water.getUnitPrice();
    }
    if (electricUnitPrice == null) {
      FeeItem electric = feeItems.stream().filter(f -> f.getName() != null && f.getName().contains("电")).findFirst().orElse(null);
      electricUnitPrice = electric == null ? null : electric.getUnitPrice();
    }

    List<FeeItem> otherFees = feeItems.stream()
        .filter(f -> f.getName() != null)
        .filter(f -> !f.getName().contains("水") && !f.getName().contains("电") && !f.getName().contains("基础房费") && !f.getName().contains("租金"))
        .toList();

    boolean isRented = RoomStatus.isRented(room.getStatus()) || activeContract != null;

    Map<String, Object> payload = new HashMap<>();
    payload.put("room", room);
    payload.put("building", building);
    payload.put("isRented", isRented);
    payload.put("activeContract", activeContract);
    payload.put("tenant", tenant);
    payload.put("appliances", appliances);
    payload.put("waterUnitPrice", waterUnitPrice);
    payload.put("electricUnitPrice", electricUnitPrice);
    payload.put("otherFees", otherFees);

    return ResponseEntity.ok(payload);
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
  public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
    Room before = roomService.findById(id).orElse(null);
    if (before == null) {
      return ResponseEntity.notFound().build();
    }
    if (isRoomRented(before)) {
      return ResponseEntity.badRequest().body("房间" + before.getRoomNumber() + "已出租，不能删除。");
    }
    OperationSnapshot snapshot = operationSnapshotService.capture(
        "房屋管理", "delete", "room", id, before.getRoomNumber(), before);
    roomService.delete(id);
    operationLogService.log(
        "room", "delete", String.valueOf(id), before.getRoomNumber(), before, null,
        "删除房间: " + before.getRoomNumber(), request, true, null, null, Map.of("roomId", id));
    return ResponseEntity.ok(UndoResponse.of(snapshot));
  }

  @PostMapping("/batch-delete")
  @Transactional
  public ResponseEntity<?> batchDelete(@RequestBody Map<String, Object> body) {
    Object roomIdsRaw = body == null ? null : body.get("roomIds");
    if (!(roomIdsRaw instanceof List<?> idList) || idList.isEmpty()) {
      return ResponseEntity.badRequest().body("请先选择要删除的房间。");
    }

    List<Long> roomIds = new ArrayList<>();
    for (Object val : idList) {
      if (!(val instanceof Number number)) {
        return ResponseEntity.badRequest().body("roomIds 参数格式错误。");
      }
      roomIds.add(number.longValue());
    }

    List<Room> rooms = roomService.listByIds(roomIds);
    if (rooms.size() != roomIds.size()) {
      return ResponseEntity.badRequest().body("部分房间不存在，请刷新后重试。");
    }

    List<String> blocked = new ArrayList<>();
    for (Room room : rooms) {
      if (isRoomRented(room)) {
        blocked.add(room.getRoomNumber());
      }
    }
    if (!blocked.isEmpty()) {
      return ResponseEntity.badRequest().body("以下房间已出租，不能删除：" + String.join("、", blocked));
    }

    List<Long> undoSnapshotIds = new ArrayList<>();
    String undoExpireAt = null;
    for (Room room : rooms) {
      OperationSnapshot snapshot = operationSnapshotService.capture(
          "房屋管理", "delete", "room", room.getId(), room.getRoomNumber(), room);
      undoSnapshotIds.add(snapshot.getId());
      if (snapshot.getExpiredAt() != null) {
        undoExpireAt = snapshot.getExpiredAt().toString();
      }
      roomService.delete(room.getId());
    }
    return ResponseEntity.ok(Map.of(
        "deletedCount", rooms.size(),
        "undoSnapshotIds", undoSnapshotIds,
        "undoExpireAt", undoExpireAt,
        "undoTip", "批量删除成功，可在30分钟内撤销"));
  }

  private boolean isRoomRented(Room room) {
    if (room == null) {
      return false;
    }
    if (RoomStatus.isRented(room.getStatus())) {
      return true;
    }
    return !contractRepository.findByRoomIdAndStatusIn(room.getId(), ContractStatus.ACTIVE_RAW_VALUES).isEmpty();
  }
}
