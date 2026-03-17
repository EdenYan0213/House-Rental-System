package com.house.controller;

import com.house.ai.AiAssistantRequest;
import com.house.ai.AiAssistantResponse;
import com.house.ai.AiExecutionOutcome;
import com.house.ai.AiAssistantService;
import com.house.model.User;
import com.house.service.OperationLogService;
import com.house.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api/ai-assistant")
public class AiAssistantController {

  private final AiAssistantService aiAssistantService;
  private final OperationLogService operationLogService;
  private final UserService userService;
  private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

  public AiAssistantController(AiAssistantService aiAssistantService,
      OperationLogService operationLogService,
      UserService userService) {
    this.aiAssistantService = aiAssistantService;
    this.operationLogService = operationLogService;
    this.userService = userService;
  }

  @PostMapping("/execute")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public ResponseEntity<?> execute(@RequestBody AiAssistantRequest request, HttpServletRequest httpRequest) {
    if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "指令不能为空"));
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String username = auth == null ? null : auth.getName();
    User user = username == null ? null : userService.findByUsername(username);
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("message", "未登录或用户不存在"));
    }

    ReentrantLock lock = userLocks.computeIfAbsent(user.getId(), id -> new ReentrantLock());
    if (!lock.tryLock()) {
      return ResponseEntity.ok(AiAssistantResponse.fail("当前已有指令在处理中，请稍后再试。", "UNKNOWN"));
    }

    long start = System.currentTimeMillis();
    String intent = "UNKNOWN";
    String entities = "{}";
    String modelName = "";

    try {
      AiExecutionOutcome outcome = aiAssistantService.execute(request.getMessage());
      intent = outcome.getIntent();
      entities = outcome.getEntitiesJson();
      modelName = outcome.getModelName();

      long duration = System.currentTimeMillis() - start;
      operationLogService.logAi(
          user.getId(),
          request.getMessage(),
          intent,
          entities,
          outcome.getResponse().isSuccess() ? "SUCCESS" : "FAILED",
          outcome.getResponse().getMessage(),
          duration,
          modelName,
          httpRequest);

      return ResponseEntity.ok(outcome.getResponse());
    } catch (Exception ex) {
      long duration = System.currentTimeMillis() - start;
      operationLogService.logAi(
          user.getId(),
          request.getMessage(),
          intent,
          entities,
          "FAILED",
          ex.getMessage(),
          duration,
          modelName,
          httpRequest);

      return ResponseEntity.status(500).body(AiAssistantResponse.fail("AI助手处理失败，请稍后重试。", "UNKNOWN"));
    } finally {
      lock.unlock();
    }
  }
}
