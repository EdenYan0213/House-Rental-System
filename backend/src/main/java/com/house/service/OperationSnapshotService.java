package com.house.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.Bill;
import com.house.model.BillStatus;
import com.house.model.Contract;
import com.house.model.Building;
import com.house.model.MeterReading;
import com.house.model.OperationSnapshot;
import com.house.model.RepairOrder;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.BuildingRepository;
import com.house.repo.BillRepository;
import com.house.repo.ContractRepository;
import com.house.repo.MeterReadingRepository;
import com.house.repo.OperationSnapshotRepository;
import com.house.repo.RepairOrderRepository;
import com.house.repo.RoomRepository;
import com.house.repo.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class OperationSnapshotService {
  private final OperationSnapshotRepository operationSnapshotRepository;
  private final TenantRepository tenantRepository;
  private final ContractRepository contractRepository;
  private final BillRepository billRepository;
  private final BuildingRepository buildingRepository;
  private final MeterReadingRepository meterReadingRepository;
  private final RepairOrderRepository repairOrderRepository;
  private final RoomRepository roomRepository;
  private final ObjectMapper objectMapper;
  private final OperationLogService operationLogService;
  private final JdbcTemplate jdbcTemplate;
  private final long undoWindowMinutes;
  private final long retentionDays;

  public OperationSnapshotService(OperationSnapshotRepository operationSnapshotRepository,
      TenantRepository tenantRepository,
      ContractRepository contractRepository,
      BillRepository billRepository,
      BuildingRepository buildingRepository,
      MeterReadingRepository meterReadingRepository,
      RepairOrderRepository repairOrderRepository,
      RoomRepository roomRepository,
      ObjectMapper objectMapper,
      OperationLogService operationLogService,
      JdbcTemplate jdbcTemplate,
      @Value("${app.snapshot.undo-window-minutes:30}") long undoWindowMinutes,
      @Value("${app.snapshot.history-retention-days:30}") long retentionDays) {
    this.operationSnapshotRepository = operationSnapshotRepository;
    this.tenantRepository = tenantRepository;
    this.contractRepository = contractRepository;
    this.billRepository = billRepository;
    this.buildingRepository = buildingRepository;
    this.meterReadingRepository = meterReadingRepository;
    this.repairOrderRepository = repairOrderRepository;
    this.roomRepository = roomRepository;
    this.objectMapper = objectMapper;
    this.operationLogService = operationLogService;
    this.jdbcTemplate = jdbcTemplate;
    this.undoWindowMinutes = undoWindowMinutes;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "${app.snapshot.expired-cleanup-cron:0 */30 * * * *}")
  @Transactional
  public void cleanupExpiredUnrecoverable() {
    operationSnapshotRepository.deleteByExpiredAtBeforeAndIsRecoveredFalse(LocalDateTime.now());
  }

  @Transactional
  public OperationSnapshot capture(String module, String operation, String tableName,
      Long targetId, String targetName, Object beforeObject) {
    OperationSnapshot snapshot = new OperationSnapshot();
    snapshot.setUserId(null);
    snapshot.setUsername(resolveOperator());
    snapshot.setModule(module);
    snapshot.setOperation(operation);
    snapshot.setTableName(tableName);
    snapshot.setTargetId(targetId);
    snapshot.setTargetName(targetName);
    snapshot.setBeforeData(toJson(beforeObject));
    snapshot.setIsRecovered(false);
    snapshot.setCreatedAt(LocalDateTime.now());
    snapshot.setExpiredAt(LocalDateTime.now().plusMinutes(Math.max(1, undoWindowMinutes)));
    return operationSnapshotRepository.save(snapshot);
  }

  @Scheduled(cron = "${app.snapshot.cleanup-cron:0 0 */6 * * *}")
  @Transactional
  public void cleanupOldHistory() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
    operationSnapshotRepository.deleteByCreatedAtBefore(cutoff);
  }

  @Transactional
  public void complete(Long snapshotId, Object afterObject) {
    if (snapshotId == null) {
      return;
    }
    operationSnapshotRepository.findById(snapshotId).ifPresent(snapshot -> {
      snapshot.setAfterData(toJson(afterObject));
      operationSnapshotRepository.save(snapshot);
    });
  }

  public Page<OperationSnapshot> search(String username, String module, String keyword,
      String startDate, String endDate, int page, int size) {
    Specification<OperationSnapshot> spec = (root, query, cb) -> cb.conjunction();
    // Undo management only shows currently recoverable operations.
    spec = spec.and((root, query, cb) -> cb.isFalse(root.get("isRecovered")));
    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("expiredAt"), LocalDateTime.now()));
    if (username != null && !username.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("username"), username));
    }
    if (module != null && !module.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("module"), module));
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim() + "%";
      spec = spec.and((root, query, cb) -> cb.or(
          cb.like(root.get("targetName"), like),
          cb.like(root.get("operation"), like),
          cb.like(root.get("tableName"), like)));
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
    }
    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return operationSnapshotRepository.findAll(spec, pageable);
  }

  public OperationSnapshot getOrThrow(Long id) {
    return operationSnapshotRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("操作快照不存在"));
  }

  @Transactional
  public Map<String, Object> recover(Long id, HttpServletRequest request) {
    OperationSnapshot snapshot = getOrThrow(id);
    if (Boolean.TRUE.equals(snapshot.getIsRecovered())) {
      throw new IllegalStateException("该操作已恢复，不能重复恢复");
    }
    if (snapshot.getExpiredAt() != null && snapshot.getExpiredAt().isBefore(LocalDateTime.now())) {
      throw new IllegalStateException("撤销窗口已过期，无法恢复");
    }

    switch (snapshot.getTableName()) {
      case "tenant" -> recoverTenant(snapshot);
      case "contract" -> recoverContract(snapshot);
      case "bill" -> recoverBill(snapshot);
      case "repair_order" -> recoverRepairOrder(snapshot);
      case "building" -> recoverBuilding(snapshot);
      case "room" -> recoverRoom(snapshot);
      case "meter_reading" -> recoverMeterReading(snapshot);
      default -> throw new IllegalStateException("暂不支持该类型的恢复: " + snapshot.getTableName());
    }

    LocalDateTime recoveredAt = LocalDateTime.now();
    operationSnapshotRepository.delete(snapshot);
    operationLogService.log("操作恢复", "恢复 " + snapshot.getModule() + " - " + snapshot.getTargetName(), request);

    Map<String, Object> result = new HashMap<>();
    result.put("id", snapshot.getId());
    result.put("status", "RECOVERED");
    result.put("recoveredAt", recoveredAt);
    return result;
  }

  private void recoverTenant(OperationSnapshot snapshot) {
    Tenant before = fromJson(snapshot.getBeforeData(), Tenant.class);
    Tenant current = before.getId() == null ? null : tenantRepository.findById(before.getId()).orElse(null);
    checkConflict(snapshot, current);
    tenantRepository.save(before);
  }

  private void recoverContract(OperationSnapshot snapshot) {
    JsonNode beforeNode = readTree(snapshot.getBeforeData());
    Contract beforeContract;
    Room beforeRoom = null;

    if (beforeNode.isObject() && beforeNode.has("contract")) {
      beforeContract = objectMapper.convertValue(beforeNode.get("contract"), Contract.class);
      if (beforeNode.has("room") && !beforeNode.get("room").isNull()) {
        beforeRoom = objectMapper.convertValue(beforeNode.get("room"), Room.class);
      }
    } else {
      beforeContract = objectMapper.convertValue(beforeNode, Contract.class);
    }

    Contract current = beforeContract.getId() == null ? null
        : contractRepository.findById(beforeContract.getId()).orElse(null);
    checkConflict(snapshot, current);
    contractRepository.save(beforeContract);
    if (beforeRoom != null) {
      roomRepository.save(beforeRoom);
    }
  }

  private void recoverBill(OperationSnapshot snapshot) {
    Bill before = fromJson(snapshot.getBeforeData(), Bill.class);
    Bill current = before.getId() == null ? null : billRepository.findById(before.getId()).orElse(null);
    if ("void".equalsIgnoreCase(snapshot.getOperation())) {
      if (current == null) {
        throw new IllegalStateException("目标数据不存在，无法恢复");
      }
      String currentStatus = current.getStatus();
      boolean inVoidState = BillStatus.isVoid(currentStatus);
      if (!inVoidState) {
        throw new IllegalStateException("账单状态已变化，无法恢复");
      }
    } else {
      checkConflict(snapshot, current);
    }
    billRepository.save(before);
  }

  private void recoverRepairOrder(OperationSnapshot snapshot) {
    RepairOrder before = fromJson(snapshot.getBeforeData(), RepairOrder.class);
    RepairOrder current = before.getId() == null ? null : repairOrderRepository.findById(before.getId()).orElse(null);
    checkConflict(snapshot, current);
    repairOrderRepository.save(before);
  }

  private void recoverBuilding(OperationSnapshot snapshot) {
    Building before = fromJson(snapshot.getBeforeData(), Building.class);
    Building current = before.getId() == null ? null : buildingRepository.findById(before.getId()).orElse(null);
    checkConflict(snapshot, current);
    if ("delete".equalsIgnoreCase(snapshot.getOperation()) && current == null && before.getId() != null) {
      jdbcTemplate.update(
          "INSERT INTO building (id, name, code, address, image_url, total_floors, water_unit_price, electric_unit_price) VALUES (?,?,?,?,?,?,?,?)",
          before.getId(),
          before.getName(),
          before.getCode(),
          before.getAddress(),
          before.getImageUrl(),
          before.getTotalFloors(),
          before.getWaterUnitPrice(),
          before.getElectricUnitPrice());
      return;
    }
    buildingRepository.save(before);
  }

  private void recoverRoom(OperationSnapshot snapshot) {
    Room before = fromJson(snapshot.getBeforeData(), Room.class);
    Room current = before.getId() == null ? null : roomRepository.findById(before.getId()).orElse(null);
    checkConflict(snapshot, current);
    if ("delete".equalsIgnoreCase(snapshot.getOperation()) && current == null && before.getId() != null) {
      jdbcTemplate.update(
          "INSERT INTO room (id, building_id, floor, room_number, layout, area, status, water_meter_id, electric_meter_id, image_url, deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
          before.getId(),
          before.getBuildingId(),
          before.getFloor(),
          before.getRoomNumber(),
          before.getLayout(),
          before.getArea(),
          before.getStatus(),
          before.getWaterMeterId(),
          before.getElectricMeterId(),
          before.getImageUrl(),
          before.getDeleted());
      return;
    }
    roomRepository.save(before);
  }

  private void recoverMeterReading(OperationSnapshot snapshot) {
    MeterReading before = fromJson(snapshot.getBeforeData(), MeterReading.class);
    MeterReading current = before.getId() == null ? null : meterReadingRepository.findById(before.getId()).orElse(null);
    checkConflict(snapshot, current);
    meterReadingRepository.save(before);
  }

  private void checkConflict(OperationSnapshot snapshot, Object currentObject) {
    String operation = snapshot.getOperation();
    if ("delete".equalsIgnoreCase(operation)) {
      if (currentObject != null) {
        throw new IllegalStateException("目标数据已存在，无法恢复删除操作");
      }
      return;
    }

    if (currentObject == null) {
      throw new IllegalStateException("目标数据不存在，无法恢复");
    }

    if (snapshot.getAfterData() == null || snapshot.getAfterData().isBlank()) {
      return;
    }

    String currentJson = canonicalJson(toJson(currentObject));
    String afterJson = canonicalJson(snapshot.getAfterData());
    if (!currentJson.equals(afterJson)) {
      throw new IllegalStateException("数据已被后续修改，无法恢复");
    }
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("快照数据损坏");
    }
  }

  private <T> T fromJson(String json, Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (Exception e) {
      throw new IllegalStateException("快照数据解析失败");
    }
  }

  public Map<String, Object> parseJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (Exception e) {
      return Map.of("raw", json);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("快照序列化失败");
    }
  }

  private String canonicalJson(String json) {
    try {
      JsonNode node = objectMapper.readTree(json);
      return objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      return json == null ? "" : json;
    }
  }

  private String resolveOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) {
      return "anonymous";
    }
    return auth.getName();
  }
}
