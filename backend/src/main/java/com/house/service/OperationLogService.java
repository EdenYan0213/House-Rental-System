package com.house.service;

import com.house.model.OperationLog;
import com.house.repo.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationLogService {
  private final OperationLogRepository operationLogRepository;

  public OperationLogService(OperationLogRepository operationLogRepository) {
    this.operationLogRepository = operationLogRepository;
  }

  public void log(String module, String action, HttpServletRequest request) {
    OperationLog log = new OperationLog();
    log.setModule(module);
    log.setAction(action);
    log.setOperator(resolveOperator());
    log.setIp(resolveIp(request));
    log.setOperationTime(LocalDateTime.now());
    log.setCreatedAt(LocalDateTime.now());
    operationLogRepository.save(log);
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
    log.setModule("AI助手");
    log.setAction("自然语言指令执行");
    log.setOperator(resolveOperator());
    log.setIp(resolveIp(request));
    log.setOperationTime(LocalDateTime.now());
    log.setCreatedAt(LocalDateTime.now());
    log.setAiInstruction(instruction);
    log.setAiIntent(intent);
    log.setAiEntities(entities);
    log.setAiStatus(status);
    log.setAiResponseMessage(responseMessage);
    log.setAiDurationMs(durationMs);
    log.setAiModelName(modelName);
    operationLogRepository.save(log);
  }

  public Page<OperationLog> search(String operator, String startDate, String endDate, int page, int size) {
    Specification<OperationLog> spec = (root, query, cb) -> cb.conjunction();

    if (operator != null && !operator.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("operator"), operator));
    }
    if (startDate != null && !startDate.isBlank()) {
      LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("operationTime"), start));
    }
    if (endDate != null && !endDate.isBlank()) {
      LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("operationTime"), end));
    }

    PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operationTime"));
    return operationLogRepository.findAll(spec, pageable);
  }

  public List<OperationLog> searchForExport(String operator, String startDate, String endDate) {
    return search(operator, startDate, endDate, 0, 100000).getContent();
  }

  private String resolveOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getName() == null) {
      return "anonymous";
    }
    return auth.getName();
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
