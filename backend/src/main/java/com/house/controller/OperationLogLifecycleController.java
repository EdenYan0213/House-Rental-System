package com.house.controller;

import com.house.service.OperationLogLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/log-lifecycle")
public class OperationLogLifecycleController {
  private final OperationLogLifecycleService lifecycleService;

  public OperationLogLifecycleController(OperationLogLifecycleService lifecycleService) {
    this.lifecycleService = lifecycleService;
  }

  @PostMapping("/run")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public Map<String, Object> runNow() {
    return lifecycleService.archiveAndCleanup();
  }
}
