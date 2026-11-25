package com.house.controller;

import com.house.model.RemindRule;
import com.house.model.RemindLog;
import com.house.model.SmsConfig;
import com.house.model.SystemMessage;
import com.house.repo.RemindLogRepository;
import com.house.repo.RemindRuleRepository;
import com.house.repo.SmsConfigRepository;
import com.house.repo.SystemMessageRepository;
import com.house.service.RemindService;
import com.house.service.SmsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/remind")
public class RemindController {
  private final RemindRuleRepository remindRuleRepository;
  private final SmsConfigRepository smsConfigRepository;
  private final RemindLogRepository remindLogRepository;
  private final SmsService smsService;
  private final RemindService remindService;
  private final SystemMessageRepository systemMessageRepository;

  public RemindController(RemindRuleRepository remindRuleRepository, SmsConfigRepository smsConfigRepository,
      RemindLogRepository remindLogRepository, SmsService smsService, RemindService remindService,
      SystemMessageRepository systemMessageRepository) {
    this.remindRuleRepository = remindRuleRepository;
    this.smsConfigRepository = smsConfigRepository;
    this.remindLogRepository = remindLogRepository;
    this.smsService = smsService;
    this.remindService = remindService;
    this.systemMessageRepository = systemMessageRepository;
  }

  @GetMapping("/rules")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public List<RemindRule> listRules() {
    return remindRuleRepository.findAll();
  }

  @PostMapping("/rules")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RemindRule saveRule(@RequestBody RemindRule rule) {
    return remindRuleRepository.save(rule);
  }

  @GetMapping("/sms/config")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public List<SmsConfig> getSmsConfig() {
    return smsConfigRepository.findAll();
  }

  @PostMapping("/sms/config")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public SmsConfig saveSmsConfig(@RequestBody SmsConfig cfg) {
    return smsConfigRepository.save(cfg);
  }

  /**
   * 催缴记录列表。
   *
   * <p>默认只返回催缴场景（REMIND），验证码短信不混进来；
   * 传 {@code scene=ALL} 可查看全部短信记录用于排查。
   */
  @GetMapping("/logs")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public List<RemindLog> listLogs(@RequestParam(value = "scene", required = false) String scene) {
    if ("ALL".equalsIgnoreCase(scene)) {
      return remindLogRepository.findAllByOrderByCreatedAtDesc();
    }
    return remindLogRepository.findBySceneOrderByCreatedAtDesc(RemindLog.SCENE_REMIND);
  }

  @PostMapping("/sms/test")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public RemindLog testSend(@RequestBody RemindLog log) {
    log.setScene(RemindLog.SCENE_TEST);
    return smsService.sendSms(log);
  }

  @PostMapping("/trigger")
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
  public Map<String, Object> triggerRemindNow() {
    int sentCount = remindService.triggerRemindsNow();
    SystemMessage latest = systemMessageRepository.findTopByOrderByCreatedAtDesc().orElse(null);
    String latestTitle = latest == null ? "" : latest.getTitle();
    String latestContent = latest == null ? "" : latest.getContent();
    return Map.of(
        "success", true,
        "sentCount", sentCount,
        "latestMessageTitle", latestTitle,
        "latestMessageContent", latestContent,
        "message", "已手动触发催缴任务，发送条数: " + sentCount + "，请查看催缴记录和消息中心");
  }
}
