package com.house.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.ComplaintLog;
import com.house.model.WorkflowStatus;
import com.house.model.ContractStatus;
import com.house.model.ComplaintSuggestion;
import com.house.model.Contract;
import com.house.model.Handler;
import com.house.model.Room;
import com.house.model.User;
import com.house.repo.ComplaintLogRepository;
import com.house.repo.ComplaintSuggestionRepository;
import com.house.repo.ContractRepository;
import com.house.repo.HandlerRepository;
import com.house.repo.RoomRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 投诉业务：查询、创建、状态流转、统计。
 *
 * <p>拆分后本类只保留业务规则，两块横向能力已外置：
 * <ul>
 *   <li>{@link ComplaintScope} —— 可见楼栋范围与查询条件构建</li>
 *   <li>{@link ComplaintEnricher} —— 展示字段与操作日志（批量填充，无 N+1）</li>
 * </ul>
 *
 * <p>状态机：pending → processing → pending_confirm → completed；
 * 任意非终态可 → closed。
 */
@Service
public class ComplaintService {

  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final ComplaintLogRepository complaintLogRepository;
  private final HandlerRepository handlerRepository;
  private final RoomRepository roomRepository;
  private final ContractRepository contractRepository;
  private final ComplaintScope scope;
  private final ComplaintEnricher enricher;
  private final UserService userService;
  private final OperationLogService operationLogService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ComplaintService(ComplaintSuggestionRepository complaintSuggestionRepository,
      ComplaintLogRepository complaintLogRepository,
      HandlerRepository handlerRepository,
      RoomRepository roomRepository,
      ContractRepository contractRepository,
      ComplaintScope scope,
      ComplaintEnricher enricher,
      UserService userService,
      OperationLogService operationLogService) {
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.complaintLogRepository = complaintLogRepository;
    this.handlerRepository = handlerRepository;
    this.roomRepository = roomRepository;
    this.contractRepository = contractRepository;
    this.scope = scope;
    this.enricher = enricher;
    this.userService = userService;
    this.operationLogService = operationLogService;
  }

  // ---------------- 查询 ----------------

  public Map<String, Object> search(String status,
      String type,
      String priority,
      String startDate,
      String endDate,
      Long buildingId,
      String q,
      int page,
      int size) {
    User currentUser = getCurrentUser();
    Set<Long> scopedBuildingIds = scope.resolveScopedBuildingIds(currentUser);

    Specification<ComplaintSuggestion> spec =
        scope.buildSpec(scopedBuildingIds, status, type, priority, startDate, endDate, buildingId);
    if (spec == null) {
      return emptyPage();
    }

    List<ComplaintSuggestion> list = complaintSuggestionRepository.findAll(spec).stream()
        .sorted(Comparator.comparing(ComplaintSuggestion::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed())
        .toList();

    // 先批量补展示字段（关键词搜索依赖已填充的 tenantName / roomNumber）
    List<ComplaintSuggestion> enriched = new ArrayList<>(list);
    enricher.fill(enriched);

    if (q != null && !q.isBlank()) {
      String keyword = q.trim().toLowerCase();
      enriched = enriched.stream()
          .filter(c -> contains(c.getTitle(), keyword)
              || contains(c.getComplaintNo(), keyword)
              || contains(c.getTenantName(), keyword)
              || contains(c.getRoomNumber(), keyword))
          .toList();
    }

    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, size);
    int from = safePage * safeSize;
    int to = Math.min(enriched.size(), from + safeSize);
    List<ComplaintSuggestion> content = from >= enriched.size() ? List.of() : enriched.subList(from, to);

    int totalPages = enriched.isEmpty() ? 0 : (int) Math.ceil(enriched.size() * 1.0 / safeSize);
    return Map.of(
        "content", content,
        "totalElements", enriched.size(),
        "totalPages", totalPages);
  }

  public Optional<ComplaintSuggestion> findById(Long id) {
    Optional<ComplaintSuggestion> optional = complaintSuggestionRepository.findById(id);
    optional.ifPresent(item -> {
      if (!canAccessComplaint(item, getCurrentUser())) {
        throw new AccessDeniedException("无权查看该投诉");
      }
      enricher.fill(item);
      item.setLogs(enricher.fillLogs(item.getId()));
    });
    return optional;
  }

  public List<ComplaintSuggestion> listByTenant(Long tenantId) {
    List<ComplaintSuggestion> list =
        complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    User currentUser = getCurrentUser();
    List<ComplaintSuggestion> scoped = list.stream()
        .filter(v -> canAccessComplaint(v, currentUser))
        .toList();
    enricher.fill(scoped);
    return scoped;
  }

  public List<Handler> listEnabledHandlers() {
    return handlerRepository.findByStatusOrderByIdAsc(1);
  }

  // ---------------- 创建 ----------------

  @Transactional
  public ComplaintSuggestion create(Map<String, Object> body, HttpServletRequest request) {
    ComplaintSuggestion item = new ComplaintSuggestion();

    Long roomId = parseLong(body.get("roomId"));
    if (roomId == null) {
      throw new IllegalArgumentException("房间不能为空");
    }
    ensureCanAccessRoom(roomId);
    item.setRoomId(roomId);

    Long tenantId = parseLong(body.get("tenantId"));
    if (tenantId == null) {
      // 未指定租户时，取该房间当前生效合同的租户
      Contract active = contractRepository.findByRoomIdAndStatusIn(roomId, ContractStatus.ACTIVE_RAW_VALUES)
          .stream().findFirst().orElse(null);
      if (active != null) {
        tenantId = active.getTenantId();
      }
    }
    item.setTenantId(tenantId);

    String type = toText(body.get("type"));
    String title = toText(body.get("title"));
    String content = toText(body.get("content"));
    if (type.isBlank() || title.isBlank() || content.isBlank()) {
      throw new IllegalArgumentException("投诉类型、标题、内容不能为空");
    }

    if (tenantId != null) {
      LocalDateTime now = LocalDateTime.now();
      long count24h = complaintSuggestionRepository.countByTenantIdAndRoomIdAndCreatedAtBetween(
          tenantId, roomId, now.minusHours(24), now);
      if (count24h >= 5) {
        throw new IllegalStateException("同一租户同一房间24小时内最多提交5条投诉");
      }
    }

    item.setComplaintNo(generateComplaintNo());
    item.setReporterType(toText(body.get("reporterType")).isBlank()
        ? "admin" : toText(body.get("reporterType")));

    Long reportedBy = parseLong(body.get("reportedBy"));
    if (reportedBy == null) {
      User current = getCurrentUser();
      reportedBy = current == null ? null : current.getId();
    }
    item.setReportedBy(reportedBy);

    item.setType(type);
    item.setTitle(title);
    item.setContent(content);
    item.setImages(toJsonArray(body.get("images")));
    item.setIsAnonymous(parseIntOrDefault(body.get("isAnonymous"), 0));
    item.setPriority(toText(body.get("priority")).isBlank()
        ? "normal" : toText(body.get("priority")));
    item.setStatus(WorkflowStatus.PENDING);

    LocalDateTime now = LocalDateTime.now();
    item.setCreatedAt(now);
    item.setUpdatedAt(now);

    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);
    // 日志的操作人类型跟随实际提交方，不硬编码 admin（租客代提交时否则会记错）
    String operatorType = "tenant".equalsIgnoreCase(item.getReporterType()) ? "tenant" : "admin";
    addLog(saved.getId(), operatorType, reportedBy, "submit", "提交投诉: " + saved.getTitle());
    operationLogService.log("投诉管理", "创建投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  /**
   * 租客端提交投诉。
   *
   * <p>与后台 {@link #create} 共享同一套落库与日志逻辑，差别只在：
   * 投诉人固定为租客本人、状态从 pending 起、并做 24 小时频次限制。
   * 控制器只负责鉴权和字段格式校验，业务规则留在这里。
   */
  @Transactional
  public ComplaintSuggestion createByTenant(Long tenantId, Long roomId, String type, String title,
      String content, List<String> images) {
    LocalDateTime now = LocalDateTime.now();
    long count24h = complaintSuggestionRepository.countByTenantIdAndRoomIdAndCreatedAtBetween(
        tenantId, roomId, now.minusHours(24), now);
    if (count24h >= 5) {
      throw new IllegalStateException("同一租户同一房间24小时内最多提交5条投诉");
    }

    ComplaintSuggestion item = ComplaintSuggestion.builder()
        .complaintNo(generateComplaintNo())
        .tenantId(tenantId)
        .roomId(roomId)
        .reporterType("tenant")
        .reportedBy(tenantId)
        .type(type)
        .title(title)
        .content(content)
        .images(toJsonArray(images))
        .isAnonymous(0)
        .priority("normal")
        .status(WorkflowStatus.PENDING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);
    addLog(saved.getId(), "tenant", tenantId, "submit", "租客提交投诉: " + saved.getTitle());
    return saved;
  }

  // ---------------- 状态流转 ----------------
  @Transactional
  public ComplaintSuggestion accept(Long id, String note, Long assignedTo, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    requireStatus(item, WorkflowStatus.PENDING, "仅待受理状态可受理");

    User current = getCurrentUser();
    Long target = assignedTo;
    if (target == null && current != null) {
      target = current.getId();
    }

    item.setStatus(WorkflowStatus.PROCESSING);
    item.setAssignedTo(target);
    item.setAssignedAt(LocalDateTime.now());
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    String assignName = enricher.resolveAssigneeName(target);
    String content = "已受理" + (note == null || note.isBlank() ? "" : "，受理意见: " + note)
        + (assignName == null || assignName.isBlank() ? "" : "，处理人: " + assignName);
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "accept", content);
    operationLogService.log("投诉管理", "受理投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  @Transactional
  public ComplaintSuggestion reply(Long id, String content, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!WorkflowStatus.PROCESSING.equals(item.getStatus())
        && !WorkflowStatus.PENDING_CONFIRM.equals(item.getStatus())) {
      throw new IllegalStateException("当前状态不可回复");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("回复内容不能为空");
    }

    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "reply", content);
    operationLogService.log("投诉管理", "回复投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  @Transactional
  public ComplaintSuggestion complete(Long id, String result, boolean requireConfirm,
      HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    requireStatus(item, WorkflowStatus.PROCESSING, "仅处理中状态可完成处理");

    item.setCompletedAt(LocalDateTime.now());
    item.setStatus(requireConfirm ? WorkflowStatus.PENDING_CONFIRM : WorkflowStatus.COMPLETED);
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "complete",
        (result == null || result.isBlank() ? "完成处理" : ("完成处理: " + result))
            + (requireConfirm ? "（待确认）" : "（已完成）"));
    operationLogService.log("投诉管理", "完成投诉处理: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  @Transactional
  public ComplaintSuggestion reassign(Long id, Long assignedTo, String note, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    requireStatus(item, WorkflowStatus.PROCESSING, "仅处理中状态可转派");
    if (assignedTo == null) {
      throw new IllegalArgumentException("处理人不能为空");
    }

    item.setAssignedTo(assignedTo);
    item.setAssignedAt(LocalDateTime.now());
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    String assignName = enricher.resolveAssigneeName(assignedTo);
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "reassign",
        "转派给: " + (assignName == null || assignName.isBlank() ? assignedTo : assignName)
            + (note == null || note.isBlank() ? "" : ("，备注: " + note)));
    operationLogService.log("投诉管理", "转派投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  @Transactional
  public ComplaintSuggestion close(Long id, String reason, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (WorkflowStatus.COMPLETED.equals(item.getStatus()) || WorkflowStatus.CLOSED.equals(item.getStatus())) {
      throw new IllegalStateException("当前状态不可关闭");
    }

    item.setStatus(WorkflowStatus.CLOSED);
    item.setClosedAt(LocalDateTime.now());
    item.setClosedReason(reason);
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "close",
        "关闭投诉" + (reason == null || reason.isBlank() ? "" : ("，原因: " + reason)));
    operationLogService.log("投诉管理", "关闭投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  @Transactional
  public ComplaintSuggestion confirmComplete(Long id, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    requireStatus(item, WorkflowStatus.PENDING_CONFIRM, "仅待确认状态可确认完成");

    item.setStatus(WorkflowStatus.COMPLETED);
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "confirm", "确认完成");
    operationLogService.log("投诉管理", "确认完成投诉: " + saved.getComplaintNo(), request);

    return respond(saved);
  }

  // ---------------- 统计 ----------------

  public Map<String, Object> stats(String startDate, String endDate, Long buildingId) {
    User currentUser = getCurrentUser();
    Set<Long> scopedBuildingIds = scope.resolveScopedBuildingIds(currentUser);

    Specification<ComplaintSuggestion> spec =
        scope.buildSpec(scopedBuildingIds, null, null, null, startDate, endDate, buildingId);
    if (spec == null) {
      return emptyStats();
    }

    List<ComplaintSuggestion> list = new ArrayList<>(complaintSuggestionRepository.findAll(spec));
    enricher.fill(list);

    Map<String, Long> typeDistribution = new LinkedHashMap<>();
    Map<String, Long> statusDistribution = new LinkedHashMap<>();
    Map<String, Long> buildingDistribution = new LinkedHashMap<>();

    long timeoutCount = 0;
    List<Long> hours = new ArrayList<>();
    List<Integer> ratings = new ArrayList<>();

    for (ComplaintSuggestion item : list) {
      typeDistribution.merge(item.getType(), 1L, Long::sum);
      statusDistribution.merge(item.getStatus(), 1L, Long::sum);
      String building = item.getBuildingName() == null ? "未分配楼栋" : item.getBuildingName();
      buildingDistribution.merge(building, 1L, Long::sum);

      if (item.getCreatedAt() != null && item.getCompletedAt() != null) {
        long h = Duration.between(item.getCreatedAt(), item.getCompletedAt()).toHours();
        if (h >= 0) {
          hours.add(h);
        }
      }
      // 未完成且未关闭、超过 48 小时视为超时
      if (item.getCreatedAt() != null && item.getCompletedAt() == null && item.getClosedAt() == null) {
        if (Duration.between(item.getCreatedAt(), LocalDateTime.now()).toHours() > 48) {
          timeoutCount++;
        }
      }
      if (item.getRating() != null && item.getRating() > 0) {
        ratings.add(item.getRating());
      }
    }

    double avgHandleHours = hours.isEmpty() ? 0D : hours.stream().mapToLong(v -> v).average().orElse(0D);
    double avgRating = ratings.isEmpty() ? 0D : ratings.stream().mapToInt(v -> v).average().orElse(0D);

    return Map.of(
        "typeDistribution", typeDistribution,
        "statusDistribution", statusDistribution,
        "buildingDistribution", buildingDistribution,
        "avgHandleHours", Math.round(avgHandleHours * 10D) / 10D,
        "timeoutCount", timeoutCount,
        "avgRating", Math.round(avgRating * 10D) / 10D,
        "total", list.size());
  }

  // ---------------- 内部 ----------------

  /** 状态变更后统一回填展示字段与日志，避免每个方法各写一遍。 */
  private ComplaintSuggestion respond(ComplaintSuggestion saved) {
    enricher.fill(saved);
    saved.setLogs(enricher.fillLogs(saved.getId()));
    return saved;
  }

  private ComplaintSuggestion requireById(Long id) {
    ComplaintSuggestion item = complaintSuggestionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("投诉不存在"));
    if (!canAccessComplaint(item, getCurrentUser())) {
      throw new AccessDeniedException("无权操作该投诉");
    }
    return item;
  }

  /** 状态前置条件校验，统一错误文案。 */
  private void requireStatus(ComplaintSuggestion item, String expected, String message) {
    if (!expected.equals(item.getStatus())) {
      throw new IllegalStateException(message);
    }
  }

  private boolean canAccessComplaint(ComplaintSuggestion item, User currentUser) {
    if (item == null) {
      return false;
    }
    Set<Long> scopedBuildingIds = scope.resolveScopedBuildingIds(currentUser);
    if (scopedBuildingIds == null) {
      return true;
    }
    if (scopedBuildingIds.isEmpty() || item.getRoomId() == null) {
      return false;
    }
    Room room = roomRepository.findById(item.getRoomId()).orElse(null);
    return room != null && room.getBuildingId() != null
        && scopedBuildingIds.contains(room.getBuildingId());
  }

  private void ensureCanAccessRoom(Long roomId) {
    Set<Long> scopedBuildingIds = scope.resolveScopedBuildingIds(getCurrentUser());
    if (scopedBuildingIds == null) {
      return;
    }
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("房间不存在"));
    if (room.getBuildingId() == null || !scopedBuildingIds.contains(room.getBuildingId())) {
      throw new AccessDeniedException("无权操作该楼栋投诉");
    }
  }

  private void addLog(Long complaintId, String operatorType, Long operatorId,
      String action, String content) {
    ComplaintLog log = ComplaintLog.builder()
        .complaintId(complaintId)
        .operatorType(operatorType)
        .operatorId(operatorId)
        .action(action)
        .content(content)
        .createdAt(LocalDateTime.now())
        .build();
    complaintLogRepository.save(log);
  }

  private User getCurrentUser() {
    try {
      var auth = org.springframework.security.core.context.SecurityContextHolder
          .getContext().getAuthentication();
      if (auth == null || auth.getName() == null) {
        return null;
      }
      return userService.findByUsername(auth.getName());
    } catch (Exception e) {
      return null;
    }
  }

  private String generateComplaintNo() {
    String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    int suffix = new Random().nextInt(900) + 100;
    return "CP" + date + suffix;
  }

  private Map<String, Object> emptyPage() {
    return Map.of("content", List.of(), "totalElements", 0, "totalPages", 0);
  }

  private Map<String, Object> emptyStats() {
    return Map.of(
        "typeDistribution", Map.of(),
        "statusDistribution", Map.of(),
        "buildingDistribution", Map.of(),
        "avgHandleHours", 0,
        "timeoutCount", 0,
        "avgRating", 0,
        "total", 0);
  }

  // ---------------- 解析与序列化 ----------------

  private String toJsonArray(Object raw) {
    try {
      if (raw == null) {
        return "[]";
      }
      if (raw instanceof String text) {
        if (text.isBlank()) {
          return "[]";
        }
        if (text.startsWith("[")) {
          // 已是 JSON 数组，校验后原样返回
          objectMapper.readTree(text);
          return text;
        }
        return objectMapper.writeValueAsString(List.of(text));
      }
      if (raw instanceof List<?> list) {
        List<String> imgs = new ArrayList<>();
        for (Object o : list) {
          if (o != null) {
            imgs.add(o.toString());
          }
        }
        return objectMapper.writeValueAsString(imgs);
      }
      return "[]";
    } catch (Exception e) {
      return "[]";
    }
  }

  public List<String> parseImages(String imagesJson) {
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

  private Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return Long.parseLong(s);
  }

  private Integer parseIntOrDefault(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    String s = value.toString().trim();
    if (s.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(s);
  }

  private String toText(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  private boolean contains(String raw, String q) {
    return raw != null && raw.toLowerCase().contains(q);
  }
}
