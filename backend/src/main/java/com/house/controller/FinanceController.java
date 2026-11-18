package com.house.controller;

import com.house.dto.FinanceReportResult;
import com.house.service.FinanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

  private final FinanceReportService financeReportService;

  /**
   * 财务报表。
   *
   * @param startMonth 起始月份 yyyy-MM，默认 6 个月前
   * @param endMonth   结束月份 yyyy-MM，默认当月
   */
  @GetMapping("/report")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> report(@RequestParam(required = false) String startMonth,
      @RequestParam(required = false) String endMonth) {
    try {
      FinanceReportResult result = financeReportService.build(startMonth, endMonth);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(Map.of("message", "报表生成失败: " + e.getMessage()));
    }
  }
}
