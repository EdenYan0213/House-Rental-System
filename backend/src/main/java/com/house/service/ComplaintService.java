package com.house.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.*;
import com.house.repo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ComplaintService {
  private final ComplaintSuggestionRepository complaintSuggestionRepository;
  private final ComplaintLogRepository complaintLogRepository;
  private final HandlerRepository handlerRepository;
  private final RoomRepository roomRepository;
  private final TenantRepository tenantRepository;
  private final BuildingRepository buildingRepository;
  private final AdminBuildingScopeRepository adminBuildingScopeRepository;
  private final ContractRepository contractRepository;
  private final UserService userService;
  private final OperationLogService operationLogService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ComplaintService(ComplaintSuggestionRepository complaintSuggestionRepository,
      ComplaintLogRepository complaintLogRepository,
      HandlerRepository handlerRepository,
      RoomRepository roomRepository,
      TenantRepository tenantRepository,
      BuildingRepository buildingRepository,
      AdminBuildingScopeRepository adminBuildingScopeRepository,
      ContractRepository contractRepository,
      UserService userService,
      OperationLogService operationLogService) {
    this.complaintSuggestionRepository = complaintSuggestionRepository;
    this.complaintLogRepository = complaintLogRepository;
    this.handlerRepository = handlerRepository;
    this.roomRepository = roomRepository;
    this.tenantRepository = tenantRepository;
    this.buildingRepository = buildingRepository;
    this.adminBuildingScopeRepository = adminBuildingScopeRepository;
    this.contractRepository = contractRepository;
    this.userService = userService;
    this.operationLogService = operationLogService;
  }

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
    Set<Long> scopedBuildingIds = resolveScopedBuildingIds(currentUser);

    Specification<ComplaintSuggestion> spec = (root, query, cb) -> cb.conjunction();

    if (scopedBuildingIds != null) {
      if (scopedBuildingIds.isEmpty()) {
        return Map.of("content", List.of(), "totalElements", 0, "totalPages", 0);
      }
      List<Long> scopedRoomIds = roomRepository.findAll().stream()
          .filter(r -> r.getBuildingId() != null && scopedBuildingIds.contains(r.getBuildingId()))
          .map(Room::getId)
          .toList();
      if (scopedRoomIds.isEmpty()) {
        return Map.of("content", List.of(), "totalElements", 0, "totalPages", 0);
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(scopedRoomIds));
    }

    if (status != null && !status.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (type != null && !type.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
    }
    if (priority != null && !priority.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
    }
    if (buildingId != null) {
      if (scopedBuildingIds != null && !scopedBuildingIds.contains(buildingId)) {
        return Map.of("content", List.of(), "totalElements", 0, "totalPages", 0);
      }
      List<Long> roomIds = roomRepository.findByBuildingId(buildingId).stream().map(Room::getId).toList();
      if (roomIds.isEmpty()) {
        return Map.of("content", List.of(), "totalElements", 0, "totalPages", 0);
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(roomIds));
    }

    List<ComplaintSuggestion> list = complaintSuggestionRepository.findAll(spec).stream()
        .sorted(Comparator.comparing(ComplaintSuggestion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed())
        .toList();

    List<ComplaintSuggestion> enriched = new ArrayList<>();
    for (ComplaintSuggestion item : list) {
      fillDisplayFields(item);
      enriched.add(item);
    }

    if (q != null && !q.isBlank()) {
      String keyword = q.trim().toLowerCase();
      enriched = enriched.stream().filter(c -> contains(c.getTitle(), keyword)
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
      fillDisplayFields(item);
      item.setLogs(fillLogs(item.getId()));
    });
    return optional;
  }

  public List<ComplaintSuggestion> listByTenant(Long tenantId) {
    List<ComplaintSuggestion> list = complaintSuggestionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    User currentUser = getCurrentUser();
    List<ComplaintSuggestion> scoped = list.stream().filter(v -> canAccessComplaint(v, currentUser)).toList();
    scoped.forEach(this::fillDisplayFields);
    return scoped;
  }

  public List<Handler> listEnabledHandlers() {
    return handlerRepository.findByStatusOrderByIdAsc(1);
  }

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
      Contract active = contractRepository.findByRoomIdAndStatus(roomId, "ACTIVE").stream().findFirst().orElse(null);
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
    item.setReporterType(toText(body.get("reporterType")).isBlank() ? "admin" : toText(body.get("reporterType")));

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
    item.setPriority(toText(body.get("priority")).isBlank() ? "normal" : toText(body.get("priority")));
    item.setStatus("pending");

    LocalDateTime now = LocalDateTime.now();
    item.setCreatedAt(now);
    item.setUpdatedAt(now);

    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);
    addLog(saved.getId(), "admin", reportedBy, "submit", "提交投诉: " + saved.getTitle());
    operationLogService.log("投诉管理", "创建投诉: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion accept(Long id, String note, Long assignedTo, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!"pending".equals(item.getStatus())) {
      throw new IllegalStateException("仅待受理状态可受理");
    }

    User current = getCurrentUser();
    Long target = assignedTo;
    if (target == null && current != null) {
      target = current.getId();
    }

    item.setStatus("processing");
    item.setAssignedTo(target);
    item.setAssignedAt(LocalDateTime.now());
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    String assignName = resolveAssigneeName(target);
    String content = "已受理" + (note == null || note.isBlank() ? "" : "，受理意见: " + note)
        + (assignName == null || assignName.isBlank() ? "" : "，处理人: " + assignName);
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "accept", content);
    operationLogService.log("投诉管理", "受理投诉: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion reply(Long id, String content, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!"processing".equals(item.getStatus()) && !"pending_confirm".equals(item.getStatus())) {
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

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion complete(Long id, String result, boolean requireConfirm, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!"processing".equals(item.getStatus())) {
      throw new IllegalStateException("仅处理中状态可完成处理");
    }

    item.setCompletedAt(LocalDateTime.now());
    item.setStatus(requireConfirm ? "pending_confirm" : "completed");
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "complete",
        (result == null || result.isBlank() ? "完成处理" : ("完成处理: " + result))
            + (requireConfirm ? "（待确认）" : "（已完成）"));
    operationLogService.log("投诉管理", "完成投诉处理: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion reassign(Long id, Long assignedTo, String note, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!"processing".equals(item.getStatus())) {
      throw new IllegalStateException("仅处理中状态可转派");
    }
    if (assignedTo == null) {
      throw new IllegalArgumentException("处理人不能为空");
    }

    item.setAssignedTo(assignedTo);
    item.setAssignedAt(LocalDateTime.now());
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    String assignName = resolveAssigneeName(assignedTo);
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "reassign",
        "转派给: " + (assignName == null || assignName.isBlank() ? assignedTo : assignName)
            + (note == null || note.isBlank() ? "" : ("，备注: " + note)));
    operationLogService.log("投诉管理", "转派投诉: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion close(Long id, String reason, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if ("completed".equals(item.getStatus()) || "closed".equals(item.getStatus())) {
      throw new IllegalStateException("当前状态不可关闭");
    }

    item.setStatus("closed");
    item.setClosedAt(LocalDateTime.now());
    item.setClosedReason(reason);
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "close",
        "关闭投诉" + (reason == null || reason.isBlank() ? "" : ("，原因: " + reason)));
    operationLogService.log("投诉管理", "关闭投诉: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  @Transactional
  public ComplaintSuggestion confirmComplete(Long id, HttpServletRequest request) {
    ComplaintSuggestion item = requireById(id);
    if (!"pending_confirm".equals(item.getStatus())) {
      throw new IllegalStateException("仅待确认状态可确认完成");
    }

    item.setStatus("completed");
    item.setUpdatedAt(LocalDateTime.now());
    ComplaintSuggestion saved = complaintSuggestionRepository.save(item);

    User current = getCurrentUser();
    addLog(saved.getId(), "admin", current == null ? null : current.getId(), "confirm", "确认完成");
    operationLogService.log("投诉管理", "确认完成投诉: " + saved.getComplaintNo(), request);

    fillDisplayFields(saved);
    saved.setLogs(fillLogs(saved.getId()));
    return saved;
  }

  public Map<String, Object> stats(String startDate, String endDate, Long buildingId) {
    User currentUser = getCurrentUser();
    Set<Long> scopedBuildingIds = resolveScopedBuildingIds(currentUser);
    Specification<ComplaintSuggestion> spec = (root, query, cb) -> cb.conjunction();

    if (scopedBuildingIds != null) {
      if (scopedBuildingIds.isEmpty()) {
        return Map.of(
            "typeDistribution", Map.of(),
            "statusDistribution", Map.of(),
            "buildingDistribution", Map.of(),
            "avgHandleHours", 0,
            "timeoutCount", 0,
            "avgRating", 0,
            "total", 0);
      }
      List<Long> scopedRoomIds = roomRepository.findAll().stream()
          .filter(r -> r.getBuildingId() != null && scopedBuildingIds.contains(r.getBuildingId()))
          .map(Room::getId)
          .toList();
      if (scopedRoomIds.isEmpty()) {
        return Map.of(
            "typeDistribution", Map.of(),
            "statusDistribution", Map.of(),
            "buildingDistribution", Map.of(),
            "avgHandleHours", 0,
            "timeoutCount", 0,
            "avgRating", 0,
            "total", 0);
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(scopedRoomIds));
    }

    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), end));
    }
    if (buildingId != null) {
      if (scopedBuildingIds != null && !scopedBuildingIds.contains(buildingId)) {
        return Map.of(
            "typeDistribution", Map.of(),
            "statusDistribution", Map.of(),
            "buildingDistribution", Map.of(),
            "avgHandleHours", 0,
            "timeoutCount", 0,
            "avgRating", 0,
            "total", 0);
      }
      List<Long> roomIds = roomRepository.findByBuildingId(buildingId).stream().map(Room::getId).toList();
      if (roomIds.isEmpty()) {
        return Map.of(
            "typeDistribution", Map.of(),
            "statusDistribution", Map.of(),
            "buildingDistribution", Map.of(),
            "avgHandleHours", 0,
            "timeoutCount", 0,
            "avgRating", 0,
            "total", 0);
      }
      spec = spec.and((root, query, cb) -> root.get("roomId").in(roomIds));
    }

    List<ComplaintSuggestion> list = complaintSuggestionRepository.findAll(spec);
    list.forEach(this::fillDisplayFields);

    Map<String, Long> typeDistribution = new LinkedHashMap<>();
    Map<String, Long> statusDistribution = new LinkedHashMap<>();
    Map<String, Long> buildingDistribution = new LinkedHashMap<>();

    long timeoutCount = 0;
    List<Long> hours = new ArrayList<>();
    List<Integer> ratings = new ArrayList<>();

    for (ComplaintSuggestion item : list) {
      typeDistribution.put(item.getType(), typeDistribution.getOrDefault(item.getType(), 0L) + 1);
      statusDistribution.put(item.getStatus(), statusDistribution.getOrDefault(item.getStatus(), 0L) + 1);
      String building = item.getBuildingName() == null ? "未分配楼栋" : item.getBuildingName();
      buildingDistribution.put(building, buildingDistribution.getOrDefault(building, 0L) + 1);

      if (item.getCreatedAt() != null && item.getCompletedAt() != null) {
        long h = Duration.between(item.getCreatedAt(), item.getCompletedAt()).toHours();
        if (h >= 0) {
          hours.add(h);
        }
      }
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

  private ComplaintSuggestion requireById(Long id) {
    ComplaintSuggestion item = complaintSuggestionRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("投诉不存在"));
    if (!canAccessComplaint(item, getCurrentUser())) {
      throw new AccessDeniedException("无权操作该投诉");
    }
    return item;
  }

  private boolean canAccessComplaint(ComplaintSuggestion item, User currentUser) {
    if (item == null) {
      return false;
    }
    Set<Long> scopedBuildingIds = resolveScopedBuildingIds(currentUser);
    if (scopedBuildingIds == null) {
      return true;
    }
    if (scopedBuildingIds.isEmpty()) {
      return false;
    }
    if (item.getRoomId() == null) {
      return false;
    }
    Room room = roomRepository.findById(item.getRoomId()).orElse(null);
    return room != null && room.getBuildingId() != null && scopedBuildingIds.contains(room.getBuildingId());
  }

  private void ensureCanAccessRoom(Long roomId) {
    User currentUser = getCurrentUser();
    Set<Long> scopedBuildingIds = resolveScopedBuildingIds(currentUser);
    if (scopedBuildingIds == null) {
      return;
    }
    Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("房间不存在"));
    if (room.getBuildingId() == null || !scopedBuildingIds.contains(room.getBuildingId())) {
      throw new AccessDeniedException("无权操作该楼栋投诉");
    }
  }

  private Set<Long> resolveScopedBuildingIds(User currentUser) {
    if (currentUser == null || currentUser.getRole() == null) {
      return Set.of();
    }
    if (currentUser.getRole() == Role.SUPER_ADMIN) {
      return null;
    }
    if (currentUser.getRole() != Role.ADMIN) {
      return Set.of();
    }
    Set<Long> ids = adminBuildingScopeRepository.findByAdminId(currentUser.getId()).stream()
        .map(AdminBuildingScope::getBuildingId)
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.toSet());
    // Backward-compatible fallback: no explicit scope rows means unrestricted.
    return ids.isEmpty() ? null : ids;
  }

  private void fillDisplayFields(ComplaintSuggestion item) {
    Room room = item.getRoomId() == null ? null : roomRepository.findById(item.getRoomId()).orElse(null);
    if (room != null) {
      item.setRoomNumber(room.getRoomNumber());
      item.setBuildingId(room.getBuildingId());
      if (room.getBuildingId() != null) {
        Building building = buildingRepository.findById(room.getBuildingId()).orElse(null);
        if (building != null) {
          item.setBuildingName(building.getName());
        }
      }
    }

    Tenant tenant = item.getTenantId() == null ? null : tenantRepository.findById(item.getTenantId()).orElse(null);
    if (tenant != null) {
      item.setTenantName(tenant.getName());
      item.setTenantPhone(maskPhone(tenant.getPhone()));
      item.setTenantDisplayName(Objects.equals(item.getIsAnonymous(), 1) ? "匿名" : tenant.getName());
    } else {
      item.setTenantDisplayName("-");
    }

    item.setAssignedName(resolveAssigneeName(item.getAssignedTo()));
  }

  private List<ComplaintLog> fillLogs(Long complaintId) {
    List<ComplaintLog> logs = complaintLogRepository.findByComplaintIdOrderByCreatedAtDesc(complaintId);
    for (ComplaintLog log : logs) {
      log.setOperatorName(resolveOperatorName(log.getOperatorType(), log.getOperatorId()));
    }
    return logs;
  }

  private void addLog(Long complaintId, String operatorType, Long operatorId, String action, String content) {
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

  private String resolveAssigneeName(Long assignedTo) {
    if (assignedTo == null) {
      return "";
    }
    Handler handler = handlerRepository.findById(assignedTo).orElse(null);
    if (handler != null && handler.getName() != null) {
      return handler.getName();
    }
    User user = userService.findById(assignedTo);
    if (user == null) {
      return "";
    }
    if (user.getRealName() != null && !user.getRealName().isBlank()) {
      return user.getRealName();
    }
    return user.getUsername();
  }

  private String resolveOperatorName(String operatorType, Long operatorId) {
    if ("system".equalsIgnoreCase(operatorType)) {
      return "系统";
    }
    if (operatorId == null) {
      return "管理员";
    }
    User user = userService.findById(operatorId);
    if (user == null) {
      return "管理员";
    }
    return user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : user.getUsername();
  }

  private User getCurrentUser() {
    try {
      var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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

  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone == null ? "" : phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
}
