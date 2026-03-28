package com.house.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.config.FileStorageConfig;
import com.house.model.ComplaintLog;
import com.house.model.ComplaintSuggestion;
import com.house.model.Contract;
import com.house.model.Feedback;
import com.house.model.RenewApplication;
import com.house.model.RepairOrder;
import com.house.model.RepairWorker;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.ComplaintLogRepository;
import com.house.repo.ComplaintSuggestionRepository;
import com.house.repo.ContractRepository;
import com.house.repo.FeedbackRepository;
import com.house.repo.RenewApplicationRepository;
import com.house.repo.RepairOrderRepository;
import com.house.repo.RepairWorkerRepository;
import com.house.repo.RoomRepository;
import com.house.service.TenantTokenService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TenantPhase2Controller {
  private final TenantTokenService tenantTokenService;

  private final RepairOrderRepository repairOrderRepository;
  private final RepairWorkerRepository repairWorkerRepository;
  private final RoomRepository roomRepository;

  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final ComplaintLogRepository complaintLogRepository;

  private final ContractRepository contractRepository;
  private final RenewApplicationRepository renewApplicationRepository;
  private final FileStorageConfig fileStorageConfig;

  private final FeedbackRepository feedbackRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public TenantPhase2Controller(TenantTokenService tenantTokenService,
      RepairOrderRepository repairOrderRepository,
      RepairWorkerRepository repairWorkerRepository,
      RoomRepository roomRepository,
      ComplaintSuggestionRepository complaintSuggestionRepository,
      ComplaintLogRepository complaintLogRepository,
      ContractRepository contractRepository,
      RenewApplicationRepository renewApplicationRepository,
      FileStorageConfig fileStorageConfig,
      FeedbackRepository feedbackRepository) {
    this.tenantTokenService = tenantTokenService;
    this.repairOrderRepository = repairOrderRepository;
    this.repairWorkerRepository = repairWorkerRepository;
    this.roomRepository = roomRepository;
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.complaintLogRepository = complaintLogRepository;
    this.contractRepository = contractRepository;
    this.renewApplicationRepository = renewApplicationRepository;
    this.fileStorageConfig = fileStorageConfig;
    this.feedbackRepository = feedbackRepository;
  }

  // Phase 2: Tenant repair flow.

  @GetMapping("/repair/list")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> listRepairs(@RequestHeader("Authorization") String authHeader,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<RepairOrder> source;
    if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
      source = repairOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    } else {
      List<String> mapped = mapRepairStatusGroup(status);
      source = repairOrderRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId, mapped);
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (RepairOrder order : source) {
      rows.add(toRepairSummary(order));
    }

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int from = Math.min(safePage * safeSize, rows.size());
    int to = Math.min(from + safeSize, rows.size());

    return ResponseEntity.ok(Map.of(
        "content", rows.subList(from, to),
        "totalElements", rows.size(),
        "page", safePage,
        "size", safeSize));
  }

  @GetMapping("/repair/detail/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> repairDetail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
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
    base.put("images", parseImages(order.getImages()));
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

    if (worker != null) {
      base.put("worker", Map.of(
          "id", worker.getId(),
          "name", worker.getName() == null ? "-" : worker.getName(),
          "phone", worker.getPhone() == null ? "" : worker.getPhone()));
    } else {
      base.put("worker", null);
    }

    base.put("timeline", buildRepairTimeline(order, worker));
    return ResponseEntity.ok(base);
  }

  @PostMapping("/repair/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> createRepair(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");
    Long roomId = (Long) context.get("roomId");
    @SuppressWarnings("unchecked")
    Set<Long> roomIds = (Set<Long>) context.get("roomIds");

    Long requestRoomId = parseLong(body.get("roomId"));
    if (requestRoomId != null) {
      roomId = requestRoomId;
    }
    if (roomId == null || !roomIds.contains(roomId)) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间参数无效"));
    }

    String faultType = text(body.get("faultType"));
    String description = text(body.get("description"));
    if (faultType.isBlank() || description.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "故障类型和故障描述不能为空"));
    }
    if (description.length() > 200) {
      return ResponseEntity.badRequest().body(Map.of("message", "故障描述不能超过200字"));
    }

    List<String> images = toStringList(body.get("images"));
    if (images.size() > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "最多上传5张图片"));
    }

    String phone = text(body.get("phone"));
    if (phone.isBlank()) {
      phone = tenant.getPhone();
    }

    LocalDateTime expected = null;
    String expectedTime = text(body.get("expectedTime"));
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
        .images(toJson(images))
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

  @PostMapping("/repair/confirm/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> confirmRepair(@RequestHeader("Authorization") String authHeader,
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

    Integer rating = parseInt(body.get("rating"));
    if (rating == null || rating < 1 || rating > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "评分需在1-5之间"));
    }
    String comment = text(body.get("comment"));
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

  // Phase 2: Tenant complaint flow.

  @GetMapping("/complaint/list")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> listComplaints(@RequestHeader("Authorization") String authHeader,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<ComplaintSuggestion> source;
    if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
      source = complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    } else {
      source = complaintSuggestionRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(tenantId,
          mapComplaintStatusGroup(status));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (ComplaintSuggestion item : source) {
      rows.add(toComplaintSummary(item));
    }

    int safePage = Math.max(page, 0);
    int safeSize = Math.max(size, 1);
    int from = Math.min(safePage * safeSize, rows.size());
    int to = Math.min(from + safeSize, rows.size());

    return ResponseEntity.ok(Map.of(
        "content", rows.subList(from, to),
        "totalElements", rows.size(),
        "page", safePage,
        "size", safeSize));
  }

  @GetMapping("/complaint/detail/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> complaintDetail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    ComplaintSuggestion item = complaintSuggestionRepository.findById(id).orElse(null);
    if (item == null) {
      return ResponseEntity.notFound().build();
    }
    if (!tenantId.equals(item.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权查看该投诉"));
    }

    Room room = item.getRoomId() == null ? null : roomRepository.findById(item.getRoomId()).orElse(null);
    List<ComplaintLog> logs = complaintLogRepository.findByComplaintIdOrderByCreatedAtDesc(item.getId());

    Map<String, Object> result = new HashMap<>();
    result.put("id", item.getId());
    result.put("complaintNo", item.getComplaintNo());
    result.put("roomId", item.getRoomId());
    result.put("roomNumber", room == null ? "-" : room.getRoomNumber());
    result.put("type", item.getType());
    result.put("title", item.getTitle());
    result.put("content", item.getContent());
    result.put("images", parseImages(item.getImages()));
    result.put("status", item.getStatus());
    result.put("createdAt", item.getCreatedAt());
    result.put("rating", item.getRating());
    result.put("comment", item.getComment());

    List<Map<String, Object>> timeline = new ArrayList<>();
    for (ComplaintLog log : logs) {
      Map<String, Object> node = new HashMap<>();
      node.put("action", log.getAction());
      node.put("content", log.getContent());
      node.put("time", log.getCreatedAt());
      timeline.add(node);
    }
    result.put("timeline", timeline);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/complaint/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> createComplaint(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");
    Long roomId = (Long) context.get("roomId");
    @SuppressWarnings("unchecked")
    Set<Long> roomIds = (Set<Long>) context.get("roomIds");

    Long requestRoomId = parseLong(body.get("roomId"));
    if (requestRoomId != null) {
      roomId = requestRoomId;
    }
    if (roomId == null || !roomIds.contains(roomId)) {
      return ResponseEntity.badRequest().body(Map.of("message", "房间参数无效"));
    }

    String type = text(body.get("type"));
    String title = text(body.get("title"));
    String content = text(body.get("content"));
    if (type.isBlank() || title.isBlank() || content.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉类型、标题、内容不能为空"));
    }
    if (title.length() > 50) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉标题不能超过50字"));
    }
    if (content.length() > 500) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉内容不能超过500字"));
    }

    List<String> images = toStringList(body.get("images"));
    if (images.size() > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "最多上传5张图片"));
    }

    ComplaintSuggestion item = ComplaintSuggestion.builder()
        .complaintNo("CP" + System.currentTimeMillis())
        .tenantId(tenantId)
        .roomId(roomId)
        .reporterType("tenant")
        .reportedBy(tenantId)
        .type(type)
        .title(title)
        .content(content)
        .images(toJson(images))
        .isAnonymous(0)
        .priority("normal")
        .status("pending")
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();

    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);
    complaintLogRepository.save(ComplaintLog.builder()
        .complaintId(saved.getId())
        .operatorType("tenant")
        .operatorId(tenantId)
        .action("submit")
        .content("租客提交投诉")
        .createdAt(LocalDateTime.now())
        .build());

    return ResponseEntity.ok(Map.of("id", saved.getId(), "complaintNo", saved.getComplaintNo()));
  }

  @PostMapping("/complaint/confirm/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> confirmComplaint(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long id,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    ComplaintSuggestion item = complaintSuggestionRepository.findById(id).orElse(null);
    if (item == null) {
      return ResponseEntity.notFound().build();
    }
    if (!tenantId.equals(item.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该投诉"));
    }
    if (!"pending_confirm".equals(item.getStatus())) {
      return ResponseEntity.badRequest().body(Map.of("message", "当前状态不可确认完成"));
    }

    Integer rating = parseInt(body.get("rating"));
    if (rating == null || rating < 1 || rating > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "评分需在1-5之间"));
    }
    String comment = text(body.get("comment"));
    if (comment.length() > 200) {
      return ResponseEntity.badRequest().body(Map.of("message", "评价内容不能超过200字"));
    }

    item.setStatus("completed");
    item.setRating(rating);
    item.setComment(comment);
    item.setUpdatedAt(LocalDateTime.now());
    complaintSuggestionRepository.save(item);

    complaintLogRepository.save(ComplaintLog.builder()
        .complaintId(item.getId())
        .operatorType("tenant")
        .operatorId(tenantId)
        .action("confirm")
        .content("租客确认完成，评分: " + rating + (comment.isBlank() ? "" : ("，评价: " + comment)))
        .createdAt(LocalDateTime.now())
        .build());

    return ResponseEntity.ok(Map.of("success", true, "message", "确认完成成功"));
  }

  // Phase 2: Tenant contract and renew flow.

  @GetMapping("/contract/current")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> currentContract(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");
    Long roomId = (Long) context.get("roomId");

    Contract current = contractRepository.findByTenantIdAndRoomIdOrderByStartDateDesc(tenantId, roomId).stream()
        .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || "进行中".equals(c.getStatus()))
        .findFirst()
        .orElse(null);

    if (current == null) {
      return ResponseEntity.ok(Collections.emptyMap());
    }

    Room room = current.getRoomId() == null ? null : roomRepository.findById(current.getRoomId()).orElse(null);
    long daysToEnd = current.getEndDate() == null ? Long.MAX_VALUE
        : ChronoUnit.DAYS.between(LocalDate.now(), current.getEndDate());

    Map<String, Object> result = new HashMap<>();
    result.put("id", current.getId());
    result.put("contractNo", current.getContractNo());
    result.put("startDate", current.getStartDate());
    result.put("endDate", current.getEndDate());
    result.put("monthlyRent", current.getMonthlyRent());
    result.put("deposit", current.getDeposit());
    result.put("paymentMethod", current.getPaymentMethod());
    result.put("status", current.getStatus());
    result.put("attachmentUrl", current.getAttachmentUrl());
    result.put("roomNumber", room == null ? "-" : room.getRoomNumber());
    result.put("canApplyRenew", daysToEnd <= 90);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/contract/history")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> contractHistory(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<Contract> list = contractRepository.findByTenantIdOrderByStartDateDesc(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Contract contract : list) {
      Room room = contract.getRoomId() == null ? null : roomRepository.findById(contract.getRoomId()).orElse(null);
      Map<String, Object> row = new HashMap<>();
      row.put("id", contract.getId());
      row.put("contractNo", contract.getContractNo());
      row.put("startDate", contract.getStartDate());
      row.put("endDate", contract.getEndDate());
      row.put("status", contract.getStatus());
      row.put("monthlyRent", contract.getMonthlyRent());
      row.put("roomNumber", room == null ? "-" : room.getRoomNumber());
      row.put("attachmentUrl", contract.getAttachmentUrl());
      rows.add(row);
    }
    return ResponseEntity.ok(rows);
  }

  @GetMapping("/contract/download/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> downloadContract(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Contract contract = contractRepository.findById(id).orElse(null);
    if (contract == null) {
      return ResponseEntity.notFound().build();
    }
    if (!tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权限下载该合同"));
    }
    if (contract.getAttachmentUrl() == null || contract.getAttachmentUrl().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "当前合同未上传电子附件"));
    }

    String url = contract.getAttachmentUrl().trim();
    if (url.startsWith("http://") || url.startsWith("https://")) {
      return ResponseEntity.status(302).header(HttpHeaders.LOCATION, url).build();
    }

    String filename = url.substring(url.lastIndexOf('/') + 1);
    Path path = Paths.get(fileStorageConfig.getUploadDir()).resolve(filename).normalize();
    Resource resource = new FileSystemResource(path);
    if (!resource.exists()) {
      return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(resource);
  }

  @PostMapping("/contract/renew/apply")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> applyRenew(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    Long contractId = parseLong(body.get("contractId"));
    Integer duration = parseInt(body.get("duration"));
    String remark = text(body.get("remark"));

    if (contractId == null || duration == null || duration <= 0) {
      return ResponseEntity.badRequest().body(Map.of("message", "合同和续租时长不能为空"));
    }
    if (remark.length() > 200) {
      return ResponseEntity.badRequest().body(Map.of("message", "备注不能超过200字"));
    }

    Contract contract = contractRepository.findById(contractId).orElse(null);
    if (contract == null || !tenantId.equals(contract.getTenantId())) {
      return ResponseEntity.status(403).body(Map.of("message", "无权操作该合同"));
    }

    RenewApplication application = RenewApplication.builder()
        .contractId(contractId)
        .tenantId(tenantId)
        .expectedDuration(duration)
        .remark(remark)
        .status(0)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    RenewApplication saved = renewApplicationRepository.save(application);
    return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "申请已提交，请等待管理员处理"));
  }

  @GetMapping("/contract/renew/records")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> renewRecords(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");

    List<RenewApplication> list = renewApplicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (RenewApplication application : list) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", application.getId());
      row.put("contractId", application.getContractId());
      row.put("expectedDuration", application.getExpectedDuration());
      row.put("remark", application.getRemark());
      row.put("status", application.getStatus());
      row.put("resultRemark", application.getResultRemark());
      row.put("createdAt", application.getCreatedAt());
      rows.add(row);
    }
    return ResponseEntity.ok(rows);
  }

  // Phase 2: Tenant feedback flow.

  @PostMapping("/feedback/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> createFeedback(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");

    String type = text(body.get("type"));
    String content = text(body.get("content"));
    String phone = text(body.get("phone"));
    List<String> images = toStringList(body.get("images"));

    if (type.isBlank() || content.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "反馈类型和内容不能为空"));
    }
    if (content.length() > 500) {
      return ResponseEntity.badRequest().body(Map.of("message", "反馈内容不能超过500字"));
    }
    if (images.size() > 3) {
      return ResponseEntity.badRequest().body(Map.of("message", "最多上传3张图片"));
    }
    if (phone.isBlank()) {
      phone = tenant.getPhone();
    }

    Feedback feedback = Feedback.builder()
        .tenantId(tenant.getId())
        .type(type)
        .content(content)
        .images(toJson(images))
        .phone(phone)
        .status(0)
        .createdAt(LocalDateTime.now())
        .build();
    feedbackRepository.save(feedback);

    return ResponseEntity.ok(Map.of("success", true, "message", "感谢您的反馈"));
  }

  private Map<String, Object> toRepairSummary(RepairOrder order) {
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

  private List<Map<String, Object>> buildRepairTimeline(RepairOrder order, RepairWorker worker) {
    List<Map<String, Object>> timeline = new ArrayList<>();
    if (order.getCreatedAt() != null) {
      timeline.add(step("已提交报修", "租客", order.getCreatedAt()));
    }
    if (order.getAssignedAt() != null) {
      String op = worker == null ? "管理员" : worker.getName();
      timeline.add(step("已分配维修工: " + op, "系统", order.getAssignedAt()));
    }
    if (order.getStartTime() != null) {
      timeline.add(step("维修中", worker == null ? "维修工" : worker.getName(), order.getStartTime()));
    }
    if (order.getFinishTime() != null) {
      timeline.add(step("维修完成，待验收", worker == null ? "维修工" : worker.getName(), order.getFinishTime()));
    }
    if (order.getAcceptTime() != null) {
      timeline.add(step("租客确认完成", "租客", order.getAcceptTime()));
    }
    if ("cancelled".equals(order.getStatus()) && order.getUpdatedAt() != null) {
      timeline.add(step("工单已取消", "系统", order.getUpdatedAt()));
    }
    timeline.sort(Comparator.comparing(item -> (LocalDateTime) item.get("time")));
    return timeline;
  }

  private Map<String, Object> step(String content, String operator, LocalDateTime time) {
    Map<String, Object> item = new HashMap<>();
    item.put("content", content);
    item.put("operator", operator);
    item.put("time", time);
    return item;
  }

  private Map<String, Object> toComplaintSummary(ComplaintSuggestion item) {
    Room room = item.getRoomId() == null ? null : roomRepository.findById(item.getRoomId()).orElse(null);
    Map<String, Object> row = new HashMap<>();
    row.put("id", item.getId());
    row.put("complaintNo", item.getComplaintNo());
    row.put("type", item.getType());
    row.put("title", item.getTitle());
    row.put("status", item.getStatus());
    row.put("roomNumber", room == null ? "-" : room.getRoomNumber());
    row.put("createdAt", item.getCreatedAt());
    return row;
  }

  private List<String> parseImages(String imagesJson) {
    try {
      if (imagesJson == null || imagesJson.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
      });
    } catch (Exception e) {
      return List.of();
    }
  }

  private String toJson(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values == null ? List.of() : values);
    } catch (Exception e) {
      return "[]";
    }
  }

  private List<String> toStringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      if (item != null && !item.toString().isBlank()) {
        result.add(item.toString());
      }
    }
    return result;
  }

  private Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  private Integer parseInt(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString().trim());
    } catch (Exception e) {
      return null;
    }
  }

  private String text(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  private List<String> mapRepairStatusGroup(String status) {
    String key = status.trim().toLowerCase();
    if ("processing".equals(key)) {
      return List.of("pending", "processing", "pending_accept");
    }
    if ("completed".equals(key)) {
      return List.of("completed", "cancelled");
    }
    return List.of(status);
  }

  private List<String> mapComplaintStatusGroup(String status) {
    String key = status.trim().toLowerCase();
    if ("processing".equals(key)) {
      return List.of("pending", "processing", "pending_confirm");
    }
    if ("completed".equals(key)) {
      return List.of("completed", "closed");
    }
    return List.of(status);
  }
}
