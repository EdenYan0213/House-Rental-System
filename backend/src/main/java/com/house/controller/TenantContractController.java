package com.house.controller;

import com.house.config.FileStorageConfig;
import com.house.model.Contract;
import com.house.model.Feedback;
import com.house.model.RenewApplication;
import com.house.model.Room;
import com.house.model.Tenant;
import com.house.repo.ContractRepository;
import com.house.repo.FeedbackRepository;
import com.house.repo.RenewApplicationRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 租客端合同与续租，以及意见反馈。
 *
 * <p>从 809 行的 {@code TenantPhase2Controller} 按职责拆出。
 * 合同路径保持 {@code /api/contract/**}，反馈保持 {@code /api/feedback/create}。
 */
@RestController
@RequestMapping("/api")
public class TenantContractController {

  private static final List<String> ACTIVE_STATUS = List.of("ACTIVE", "进行中");

  private final TenantTokenService tenantTokenService;
  private final ContractRepository contractRepository;
  private final RenewApplicationRepository renewApplicationRepository;
  private final RoomRepository roomRepository;
  private final FileStorageConfig fileStorageConfig;
  private final FeedbackRepository feedbackRepository;
  private final TenantPhase2Support support;

  public TenantContractController(TenantTokenService tenantTokenService,
      ContractRepository contractRepository,
      RenewApplicationRepository renewApplicationRepository,
      RoomRepository roomRepository,
      FileStorageConfig fileStorageConfig,
      FeedbackRepository feedbackRepository,
      TenantPhase2Support support) {
    this.tenantTokenService = tenantTokenService;
    this.contractRepository = contractRepository;
    this.renewApplicationRepository = renewApplicationRepository;
    this.roomRepository = roomRepository;
    this.fileStorageConfig = fileStorageConfig;
    this.feedbackRepository = feedbackRepository;
    this.support = support;
  }

  @GetMapping("/contract/current")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> currentContract(@RequestHeader("Authorization") String authHeader) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");
    Long roomId = (Long) context.get("roomId");

    Contract current = contractRepository.findByTenantIdAndRoomIdOrderByStartDateDesc(tenantId, roomId).stream()
        .filter(c -> c.getStatus() != null && ACTIVE_STATUS.stream().anyMatch(s -> s.equalsIgnoreCase(c.getStatus())))
        .findFirst()
        .orElse(null);

    if (current == null) {
      return ResponseEntity.ok(Collections.emptyMap());
    }

    Room room = current.getRoomId() == null ? null
        : roomRepository.findById(current.getRoomId()).orElse(null);
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

    // 一次取出全部房间，避免逐条查询（N+1）
    Map<Long, Room> roomMap = new HashMap<>();
    List<Long> roomIds = list.stream()
        .map(Contract::getRoomId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (!roomIds.isEmpty()) {
      for (Room r : roomRepository.findAllById(roomIds)) {
        roomMap.put(r.getId(), r);
      }
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Contract contract : list) {
      Room room = roomMap.get(contract.getRoomId());
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

  /**
   * 下载合同附件。
   *
   * <p>外部 URL 直接 302 重定向；本地文件走 uploadDir。
   * 二者都要求合同属于当前租客，防止越权拿到别人的合同。
   */
  @GetMapping("/contract/download/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> downloadContract(@RequestHeader("Authorization") String authHeader,
      @PathVariable Long id) {
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

    Long contractId = support.parseLong(body.get("contractId"));
    Integer duration = support.parseInt(body.get("duration"));
    String remark = support.text(body.get("remark"));

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

  @PostMapping("/feedback/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> createFeedback(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Tenant tenant = (Tenant) context.get("tenant");

    String type = support.text(body.get("type"));
    String content = support.text(body.get("content"));
    String phone = support.text(body.get("phone"));
    List<String> images = support.toStringList(body.get("images"));

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
        .images(support.toJson(images))
        .phone(phone)
        .status(0)
        .createdAt(LocalDateTime.now())
        .build();
    feedbackRepository.save(feedback);

    return ResponseEntity.ok(Map.of("success", true, "message", "感谢您的反馈"));
  }
}
