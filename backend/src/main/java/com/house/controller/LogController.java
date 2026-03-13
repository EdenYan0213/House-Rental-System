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
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var resultPage = operationLogService.search(operator, startDate, endDate, page, size);

    Map<String, Object> result = new HashMap<>();
    result.put("content", resultPage.getContent());
    result.put("totalElements", resultPage.getTotalElements());
    result.put("totalPages", resultPage.getTotalPages());
    return result;
  }

  @GetMapping("/export")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<byte[]> exportLogs(
      @RequestParam(required = false) String operator,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {
    List<OperationLog> logs = operationLogService.searchForExport(operator, startDate, endDate);
    StringBuilder csv = new StringBuilder("操作时间,操作人,模块,操作内容,IP地址\n");
    for (OperationLog log : logs) {
      csv.append(safe(log.getOperationTime() == null ? "" : log.getOperationTime().toString())).append(',')
          .append(safe(log.getOperator())).append(',')
          .append(safe(log.getModule())).append(',')
          .append(safe(log.getAction())).append(',')
          .append(safe(log.getIp())).append('\n');
    }
    byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=operation_logs.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(body);
  }

  private String safe(String value) {
    if (value == null) {
      return "";
    }
    String escaped = value.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }
}
