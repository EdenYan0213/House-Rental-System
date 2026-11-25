package com.house.controller;

import com.house.model.RepairOrder;
import com.house.model.RepairWorker;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.RepairOrderRepository;
import com.house.repo.RepairWorkerRepository;
import com.house.repo.RoomRepository;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 租客端报修：列表、详情、提交、确认完成并评价。
 *
 * <p>从 809 行的 {@code TenantPhase2Controller} 按职责拆出。
 * 路径保持 {@code /api/repair/**} 不变，对前端透明。
 */
@RestController
@RequestMapping("/api/repair")
public class TenantRepairController {

  private final TenantTokenService tenantTokenService;
  private final RepairOrderRepository repairOrderRepository;
  private final RepairWorkerRepository repairWorkerRepository;
  private final RoomRepository roomRepository;
  private final TenantPhase2Support support;

  public TenantRepairController(TenantTokenService tenantTokenService,
      RepairOrderRepository repairOrderRepository,
      RepairWorkerRepository repairWorkerRepository,
      RoomRepository roomRepository,
      TenantPhase2Support support) {
    this.tenantTokenService = tenantTokenService;
    this.repairOrderRepository = repairOrderRepository;
    this.repairWorkerRepository = repairWorkerRepository;
    this.roomRepository = roomRepository;
    this.support = support;
  }

  @GetMapping("/list")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> list(@RequestHeader("Authorization") String authHeader,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<RepairOrder> source;
    if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
      source = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    } else {
      source = repairOrderRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(
          tenantId, support.repairStatusGroup(status));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (RepairOrder order : source) {
      rows.add(toSummary(order));
    }
    return ResponseEntity.ok(support.paginate(rows, page, size));
  }

  @GetMapping("/detail/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> detail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    RepairOrder order = repairOrderRepository.findById(id).orElse(null);
    if (order == null) {
      return ResponseEntity.notFound().build();
    }
    if (!tenantId.equals(order.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权限查看该报修单"));
    }

    Room room = order.getRoomId() == null ? null : roomRepository.findById(order.getRoomId()).orElse(null);
    RepairWorker worker = order.getWorkerId() == null ? null
        : repairWorkerRepository.findById(order.getWorkerId()).orElse(null);

    Map<String, Object> base = new HashMap<>();
    base.put("id", order.getId());
    base.put("orderNo", order.getOrderNo());
    base.put("roomId", order.getRoomId());
    base.put("roomNumber", room == null ? "-" : room.getRoomNumber());
    base.put("faultType", order.getFaultType());
    base.put("description", order.getDescription());
    base.put("images", support.parseImages(order.getImages()));
    base.put("status", order.getStatus());
    base.put("phone", order.getContactPhone());
    base.put("expectedTime", order.getExpectedTime());
    base.put("createdAt", order.getCreatedAt());
    base.put("materialFee", order.getMaterialFee());
    base.put("laborFee", order.getLaborFee());
    base.put("totalFee", order.getTotalFee());
    base.put("isTenantCharge", order.getIsTenantCharge());
    base.put("feeRemark", order.getFeeRemark());
    base.put("rating", order.getRating());
    base.put("comment", order.getComment());
    base.put("worker", worker == null ? null : Map.of(
        "id", worker.getId(),
        "name", worker.getName() == null ? "-" : worker.getName(),
        "phone", worker.getPhone() == null ? "" : worker.getPhone()));
    base.put("timeline", buildTimeline(order, worker));
    return ResponseEntity.ok(base);
  }

  @PostMapping("/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> create(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");
    @SuppressWarnings("unchecked")
    Set<Long> roomIds = (Set<Long>) context.get("roomIds");

    Long requestRoomId = support.parseLong(body.get("roomId"));
    if (requestRoomId != null) {
      roomId = requestRoomId;
    }
    if (roomId == null || !roomIds.contains(roomId)) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间参数无效"));
    }

    String faultType = support.text(body.get("faultType"));
    String description = support.text(body.get("description"));
    if (faultType.isBlank() || description.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "故障类型和故障描述不能为空"));
    }
    if (description.length() > 200) {
      return ResponseEntity.badRequest().body(Map.of("message", "故障描述不能超过200字"));
    }

    List<String> images = support.toStringList(body.get("images"));
    if (images.size() > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "最多上传5张图片"));
    }

    String phone = support.text(body.get("phone"));
    if (phone.isBlank()) {
      phone = tenant.getPhone();
    }

    LocalDateTime expected = null;
    String expectedTime = support.text(body.get("expectedTime"));
    if (!expectedTime.isBlank()) {
      try {
        expected = LocalDateTime.parse(expectedTime);
      } catch (DateTimeParseException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "期望维修时间格式错误，需为ISO时间"));
      }
    }

    RepairOrder order = RepairOrder.builder()
        .orderNo("RE" + System.currentTimeMillis())
        .roomId(roomId)
        .tenantId(tenant.getId())
        .reporterType("tenant")
        .reportedBy(tenant.getId())
        .faultType(faultType)
        .description(description)
        .images(support.toJson(images))
        .status("pending")
        .expectedTime(expected)
        .contactPhone(phone)
        .materialFee(0D)
        .laborFee(0D)
        .totalFee(0D)
        .isTenantCharge(0)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();

    RepairOrder saved = repairOrderRepository.save(order);
    return ResponseEntity.ok(Map.of("id", saved.getId(), "orderNo", saved.getOrderNo()));
  }

  @PostMapping("/confirm/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> confirm(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long id,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    RepairOrder order = repairOrderRepository.findById(id).orElse(null);
    if (order == null) {
      return ResponseEntity.notFound().build();
    }
    if (!tenantId.equals(order.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该报修单"));
    }
    if (!"pending_accept".equals(order.getStatus())) {
      return ResponseEntity.badRequest().body(Map.of("message", "当前状态不可确认完成"));
    }

    Integer rating = support.parseInt(body.get("rating"));
    if (rating == null || rating < 1 || rating > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "评分需在1-5之间"));
    }
    String comment = support.text(body.get("comment"));
    if (comment.length() > 200) {
      return ResponseEntity.badRequest().body(Map.of("message", "评价内容不能超过200字"));
    }

    order.setStatus("completed");
    order.setAcceptTime(LocalDateTime.now());
    order.setRating(rating);
    order.setComment(comment);
    order.setUpdatedAt(LocalDateTime.now());
    repairOrderRepository.save(order);

    return ResponseEntity.ok(Map.of("success", true, "message", "确认完成成功"));
  }

  private Map<String, Object> toSummary(RepairOrder order) {
    Room room = order.getRoomId() == null ? null : roomRepository.findById(order.getRoomId()).orElse(null);
    Map<String, Object> row = new HashMap<>();
    row.put("id", order.getId());
    row.put("orderNo", order.getOrderNo());
    row.put("roomId", order.getRoomId());
    row.put("roomNumber", room == null ? "-" : room.getRoomNumber());
    row.put("faultType", order.getFaultType());
    row.put("status", order.getStatus());
    row.put("createdAt", order.getCreatedAt());
    return row;
  }

  /** 按时间点拼装进度时间线，按时间升序。 */
  private List<Map<String, Object>> buildTimeline(RepairOrder order, RepairWorker worker) {
    List<Map<String, Object>> timeline = new ArrayList<>();
    if (order.getCreatedAt() != null) {
      timeline.add(support.timelineNode("已提交报修", "租客", order.getCreatedAt()));
    }
    if (order.getAssignedAt() != null) {
      String op = worker == null ? "管理员" : worker.getName();
      timeline.add(support.timelineNode("已分配维修工: " + op, "系统", order.getAssignedAt()));
    }
    if (order.getStartTime() != null) {
      timeline.add(support.timelineNode("维修中", worker == null ? "维修工" : worker.getName(), order.getStartTime()));
    }
    if (order.getFinishTime() != null) {
      timeline.add(support.timelineNode("维修完成，待验收",
          worker == null ? "维修工" : worker.getName(), order.getFinishTime()));
    }
    if (order.getAcceptTime() != null) {
      timeline.add(support.timelineNode("租客确认完成", "租客", order.getAcceptTime()));
    }
    if ("cancelled".equals(order.getStatus()) && order.getUpdatedAt() != null) {
      timeline.add(support.timelineNode("工单已取消", "系统", order.getUpdatedAt()));
    }
    timeline.sort(Comparator.comparing(item -> (LocalDateTime) item.get("time")));
    return timeline;
  }
}
