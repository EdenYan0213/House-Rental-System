package com.house.controller;

import com.house.model.ComplaintLog;
import com.house.model.ComplaintSuggestion;
import com.house.model.Room;
import com.house.repo.ComplaintLogRepository;
import com.house.repo.ComplaintSuggestionRepository;
import com.house.repo.RoomRepository;
import com.house.service.TenantTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 租客端投诉：列表、详情、提交、确认完成并评价。
 *
 * <p>从 809 行的 {@code TenantPhase2Controller} 按职责拆出。
 * 路径保持 {@code /api/complaint/**} 不变。
 */
@RestController
@RequestMapping("/api/complaint")
public class TenantComplaintController {

  private final TenantTokenService tenantTokenService;
  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final ComplaintLogRepository complaintLogRepository;
  private final RoomRepository roomRepository;
  private final TenantPhase2Support support;

  public TenantComplaintController(TenantTokenService tenantTokenService,
      ComplaintSuggestionRepository complaintSuggestionRepository,
      ComplaintLogRepository complaintLogRepository,
      RoomRepository roomRepository,
      TenantPhase2Support support) {
    this.tenantTokenService = tenantTokenService;
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.complaintLogRepository = complaintLogRepository;
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

    List<ComplaintSuggestion> source;
    if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
      source = complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    } else {
      source = complaintSuggestionRepository.findByTenantIdAndStatusInOrderByCreatedAtDesc(
          tenantId, support.complaintStatusGroup(status));
    }

    List<Map<String, Object>> rows = new ArrayList<>();
    for (ComplaintSuggestion item : source) {
      rows.add(toSummary(item));
    }
    return ResponseEntity.ok(support.paginate(rows, page, size));
  }

  @GetMapping("/detail/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> detail(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
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
    result.put("images", support.parseImages(item.getImages()));
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

  @PostMapping("/create")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> create(@RequestHeader("Authorization") String authHeader,
      @RequestBody Map<String, Object> body) {
    Map<String, Object> context = tenantTokenService.parseAndValidateTenant(authHeader);
    Long tenantId = (Long) context.get("tenantId");
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

    String type = support.text(body.get("type"));
    String title = support.text(body.get("title"));
    String content = support.text(body.get("content"));
    if (type.isBlank() || title.isBlank() || content.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉类型、标题、内容不能为空"));
    }
    if (title.length() > 50) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉标题不能超过50字"));
    }
    if (content.length() > 500) {
      return ResponseEntity.badRequest().body(Map.of("message", "投诉内容不能超过500字"));
    }

    List<String> images = support.toStringList(body.get("images"));
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
        .images(support.toJson(images))
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

  @PostMapping("/confirm/{id}")
  @PreAuthorize("hasRole('TENANT')")
  public ResponseEntity<?> confirm(@RequestHeader("Authorization") String authHeader,
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

    Integer rating = support.parseInt(body.get("rating"));
    if (rating == null || rating < 1 || rating > 5) {
      return ResponseEntity.badRequest().body(Map.of("message", "评分需在1-5之间"));
    }
    String comment = support.text(body.get("comment"));
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

  private Map<String, Object> toSummary(ComplaintSuggestion item) {
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
}
