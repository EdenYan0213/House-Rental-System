package com.house.controller;

import com.house.model.OperationLog;
import com.house.service.OperationLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

  private final OperationLogService operationLogService;

  public LogController(OperationLogService operationLogService) {
    this.operationLogService = operationLogService;
  }

  @GetMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, Object> list(
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String businessKeyword,
      @RequestParam(required = false) Integer logLevel,
      @RequestParam(required = false) Integer result,
      @RequestParam(required = false) Integer minDuration,
      @RequestParam(required = false) Integer maxDuration,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var resultPage = operationLogService.searchAdvanced(
        operator, startDate, endDate, module, action, businessKeyword,
        logLevel, result, minDuration, maxDuration,
        page, size);

    Map<String, Object> payload = new HashMap<>();
    payload.put("content", resultPage.getContent());
    payload.put("totalElements", resultPage.getTotalElements());
    payload.put("totalPages", resultPage.getTotalPages());
    return payload;
  }

  @GetMapping("/export")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<byte[]> exportLogs(
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String businessKeyword,
      @RequestParam(required = false) Integer logLevel,
      @RequestParam(required = false) Integer result,
      @RequestParam(required = false) Integer minDuration,
      @RequestParam(required = false) Integer maxDuration) {
    List<OperationLog> logs = operationLogService.searchForExport(operator, startDate, endDate,
        module, action, businessKeyword, logLevel, result, minDuration, maxDuration);
    StringBuilder csv = new StringBuilder("操作时间,操作人,模块,类型,业务ID,业务名称,结果,级别,耗时,摘要,IP\n");
    for (OperationLog log : logs) {
      csv.append(safe(log.getOperationTime() == null ? "" : log.getOperationTime().toString())).append(',')
          .append(safe(log.getUsername() == null ? log.getOperator() : log.getUsername())).append(',')
          .append(safe(log.getModule())).append(',')
          .append(safe(log.getAction())).append(',')
          .append(safe(log.getBusinessId())).append(',')
          .append(safe(log.getBusinessName())).append(',')
          .append(safe(log.getResult() == null ? "" : (log.getResult() == 1 ? "成功" : "失败"))).append(',')
          .append(safe(log.getLogLevel() == null ? "" : String.valueOf(log.getLogLevel()))).append(',')
          .append(safe(log.getDurationMs() == null ? "" : String.valueOf(log.getDurationMs()))).append(',')
          .append(safe(log.getChangeSummary())).append(',')
          .append(safe(log.getRequestIp() == null ? log.getIp() : log.getRequestIp())).append('\n');
    }
    byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation_logs.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(body);
  }

  @GetMapping("/filters")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, Object> filters() {
    return operationLogService.filters();
  }

  @GetMapping("/analytics")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, Object> analytics(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    return operationLogService.analytics(startDate, endDate);
  }

  private String safe(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }
}
