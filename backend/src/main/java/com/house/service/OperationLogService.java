package com.house.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.house.model.OperationLog;
import com.house.repo.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OperationLogService {
  private final OperationLogRepository operationLogRepository;
  private final ObjectMapper objectMapper;

  private static final Set<String> SENSITIVE_PARAM_KEYS = Set.of(
      "password", "oldpassword", "newpassword", "idcard", "idno", "身份证", "accesskey", "secret", "token");

  private static final Set<String> SENSITIVE_ACTION_KEYS = Set.of(
      "delete", "void", "remove_blacklist", "change_password", "reset_password", "permission", "disable", "enable");

  public OperationLogService(OperationLogRepository operationLogRepository, ObjectMapper objectMapper) {
    this.operationLogRepository = operationLogRepository;
    this.objectMapper = objectMapper;
  }

  public void log(String module, String action, HttpServletRequest request) {
    log(module, action, null, null, null, null, action, request, true, null, null, null);
  }

  public void log(String module, String action, String businessId, String businessName,
      Object before, Object after, String summary,
      HttpServletRequest request, boolean success, String failReason, Integer durationMs, Object requestBody) {
    OperationLog log = new OperationLog();
    log.setModule(module);
    log.setAction(normalizeAction(action));
    log.setBusinessId(businessId);
    log.setBusinessName(businessName);
    log.setDetailBefore(toJsonSafe(before));
    log.setDetailAfter(toJsonSafe(after));
    log.setChangeSummary(summary == null || summary.isBlank() ? action : summary);
    log.setRequestParams(maskRequestParams(requestBody));
    log.setOperator(resolveOperator());
    log.setUsername(log.getOperator());
    log.setIp(resolveIp(request));
    log.setRequestIp(log.getIp());
    log.setUserAgent(resolveUserAgent(request));
    log.setResult(success ? 1 : 0);
    log.setFailReason(failReason);
    log.setDurationMs(durationMs);
    log.setLogLevel(resolveLogLevel(log.getAction(), success));
    log.setOperationTime(LocalDateTime.now());
    log.setCreatedAt(LocalDateTime.now());
    saveAsync(log);
  }

  public void logAi(Long userId,
      String instruction,
      String intent,
      String entities,
      String status,
      String responseMessage,
      Long durationMs,
      String modelName,
      HttpServletRequest request) {
    OperationLog log = new OperationLog();
    log.setUserId(userId);
    log.setModule("AI助手");
    log.setAction("custom");
    log.setChangeSummary("自然语言指令执行");
    log.setOperator(resolveOperator());
    log.setUsername(log.getOperator());
    log.setIp(resolveIp(request));
    log.setRequestIp(log.getIp());
    log.setUserAgent(resolveUserAgent(request));
    log.setOperationTime(LocalDateTime.now());
    log.setCreatedAt(LocalDateTime.now());
    log.setResult("SUCCESS".equalsIgnoreCase(status) ? 1 : 0);
    log.setLogLevel("SUCCESS".equalsIgnoreCase(status) ? 1 : 3);
    log.setDurationMs(durationMs == null ? null : Math.toIntExact(durationMs));
    log.setAiInstruction(instruction);
    log.setAiIntent(intent);
    log.setAiEntities(entities);
    log.setAiStatus(status);
    log.setAiResponseMessage(responseMessage);
    log.setAiDurationMs(durationMs);
    log.setAiModelName(modelName);
    saveAsync(log);
  }

  public Page<OperationLog> search(String operator, String startDate, String endDate, int page, int size) {
    return searchAdvanced(operator, startDate, endDate, null, null, null, null,
        null, null, null, page, size);
  }

  public Page<OperationLog> searchAdvanced(
      String operator,
      String startDate,
      String endDate,
      String module,
      String action,
      String businessKeyword,
      Integer logLevel,
      Integer result,
      Integer minDuration,
      Integer maxDuration,
      int page,
      int size) {
    Specification<OperationLog> spec = (root, query, cb) -> cb.conjunction();

    if (operator != null && !operator.isBlank()) {
      String like = "%" + operator.trim() + "%";
      spec = spec.and((root, query, cb) -> cb.or(
          cb.like(cb.coalesce(root.get("username"), ""), like),
          cb.like(cb.coalesce(root.get("operator"), ""), like)));
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("operationTime"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("operationTime"), end));
    }
    if (module != null && !module.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("module"), module));
    }
    if (action != null && !action.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
    }
    if (businessKeyword != null && !businessKeyword.isBlank()) {
      String like = "%" + businessKeyword.trim() + "%";
      spec = spec.and((root, query, cb) -> cb.or(
          cb.like(cb.coalesce(root.get("businessId"), ""), like),
          cb.like(cb.coalesce(root.get("businessName"), ""), like)));
    }
    if (logLevel != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("logLevel"), logLevel));
    }
    if (result != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), result));
    }
    if (minDuration != null) {
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("durationMs"), minDuration));
    }
    if (maxDuration != null) {
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("durationMs"), maxDuration));
    }

    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operationTime"));
    return operationLogRepository.findAll(spec, pageable);
  }

  public List<OperationLog> searchForExport(String operator, String startDate, String endDate,
      String module, String action, String businessKeyword,
      Integer logLevel, Integer result, Integer minDuration, Integer maxDuration) {
    return searchAdvanced(operator, startDate, endDate, module, action, businessKeyword,
        logLevel, result, minDuration, maxDuration, 0, 100000).getContent();
  }

  public Map<String, Object> filters() {
    Map<String, Object> out = new HashMap<>();
    out.put("operators", operationLogRepository.findDistinctOperators());
    out.put("modules", operationLogRepository.findDistinctModules());
    // 动作在库内是英文常量（add/edit/delete…），对外返回中文便于筛选下拉直接展示
    out.put("actions", operationLogRepository.findDistinctActions().stream()
        .map(this::actionText)
        .distinct()
        .toList());
    return out;
  }

  public Map<String, Object> analytics(String startDate, String endDate) {
    LocalDateTime start = toStart(startDate);
    LocalDateTime end = toEnd(endDate);

    Map<String, Object> result = new HashMap<>();
    result.put("trend", mapRows(operationLogRepository.trend(start, end), List.of("date", "count")));
    result.put("moduleDistribution",
        mapRows(operationLogRepository.moduleDistribution(start, end), List.of("module", "count")));
    result.put("topOperators",
        mapRows(operationLogRepository.topOperators(start, end, 10), List.of("operator", "count")));
    result.put("slowTop",
        mapRows(operationLogRepository.slowTop(start, end, 10), List.of("module", "action", "summary", "durationMs")));
    result.put("failStats",
        mapRows(operationLogRepository.failStats(start, end), List.of("module", "action", "count")));
    return result;
  }

  @Async
  public void saveAsync(OperationLog log) {
    operationLogRepository.save(log);
  }

  private LocalDateTime toStart(String startDate) {
    if (startDate == null || startDate.isBlank()) {
      return LocalDate.now().minusDays(30).atStartOfDay();
    }
    return LocalDate.parse(startDate).atStartOfDay();
  }

  private LocalDateTime toEnd(String endDate) {
    if (endDate == null || endDate.isBlank()) {
      return LocalDateTime.now();
    }
    return LocalDate.parse(endDate).atTime(23, 59, 59);
  }

  private List<Map<String, Object>> mapRows(List<Object[]> rows, List<String> keys) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (Object[] row : rows) {
      Map<String, Object> item = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) {
        item.put(keys.get(i), row.length > i ? row[i] : null);
      }
      list.add(item);
    }
    return list;
  }

  /** 动作英文字面量 → 中文展示名（与前端 labels.js 保持一致）。 */
  private String actionText(String action) {
    if (action == null) {
      return "其他操作";
    }
    return switch (action.toLowerCase(Locale.ROOT)) {
      case "login" -> "登录";
      case "logout" -> "登出";
      case "add" -> "新增";
      case "edit" -> "编辑";
      case "delete" -> "删除";
      case "void" -> "作废";
      case "import" -> "导入";
      case "export" -> "导出";
      case "pay" -> "收款";
      case "renew" -> "续租";
      case "terminate" -> "解约";
      case "move_out" -> "退租";
      default -> "其他操作";
    };
  }

  private String normalizeAction(String action) {
    if (action == null || action.isBlank()) {
      return "custom";
    }
    String lower = action.toLowerCase(Locale.ROOT);
    if (lower.contains("delete") || lower.contains("删除"))
      return "delete";
    if (lower.contains("edit") || lower.contains("update") || lower.contains("修改"))
      return "edit";
    if (lower.contains("add") || lower.contains("create") || lower.contains("新增") || lower.contains("创建"))
      return "add";
    if (lower.contains("void") || lower.contains("作废"))
      return "void";
    if (lower.contains("import") || lower.contains("导入"))
      return "import";
    if (lower.contains("export") || lower.contains("导出"))
      return "export";
    if (lower.contains("login") || lower.contains("登录"))
      return "login";
    if (lower.contains("logout") || lower.contains("登出") || lower.contains("注销"))
      return "logout";
    return "custom";
  }

  private Integer resolveLogLevel(String action, boolean success) {
    if (!success) {
      return 3;
    }
    if (action != null && SENSITIVE_ACTION_KEYS.contains(action.toLowerCase(Locale.ROOT))) {
      return 2;
    }
    return 1;
  }

  public String maskRequestParams(Object requestBody) {
    if (requestBody == null) {
      return null;
    }
    if (requestBody instanceof Map<?, ?> sourceMap) {
      Map<String, Object> masked = new HashMap<>();
      sourceMap.forEach((k, v) -> {
        String key = String.valueOf(k);
        String lower = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_PARAM_KEYS.contains(lower)) {
          masked.put(key, "***");
        } else {
          masked.put(key, v);
        }
      });
      return toJsonSafe(masked);
    }
    return toJsonSafe(requestBody);
  }

  private String toJsonSafe(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }

  private String resolveOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // Spring Security 会为未认证请求注入匿名 Authentication，
    // 其 getName() 返回 "anonymousUser"，这里统一归为「未登录用户」。
    if (auth == null || auth.getName() == null
        || !auth.isAuthenticated()
        || "anonymousUser".equals(auth.getName())) {
      return "未登录用户";
    }
    return auth.getName();
  }

  private String resolveUserAgent(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    return request.getHeader("User-Agent");
  }

  private String resolveIp(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
