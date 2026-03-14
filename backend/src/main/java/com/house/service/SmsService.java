package com.house.service;

import com.house.model.RemindLog;
import com.house.model.SmsConfig;
import com.house.model.SystemMessage;
import com.house.repo.RemindLogRepository;
import com.house.repo.SmsConfigRepository;
import com.house.repo.SystemMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SmsService {
  private static final Logger logg = LoggerFactory.getLogger(SmsService.class);

  private final SmsConfigRepository smsConfigRepository;
  private final RemindLogRepository remindLogRepository;
  private final SystemMessageRepository systemMessageRepository;

  public SmsService(SmsConfigRepository smsConfigRepository, RemindLogRepository remindLogRepository,
      SystemMessageRepository systemMessageRepository) {
    this.smsConfigRepository = smsConfigRepository;
    this.remindLogRepository = remindLogRepository;
    this.systemMessageRepository = systemMessageRepository;
  }

  public RemindLog sendSms(RemindLog log) {
    // fetch sms config (use first available)
    Optional<SmsConfig> cfgOpt = smsConfigRepository.findAll().stream().findFirst();
    boolean debug = true;
    SmsConfig cfg = null;
    if (cfgOpt.isPresent()) {
      cfg = cfgOpt.get();
      debug = cfg.getDebugMode() == null ? true : (cfg.getDebugMode() == 1);
    }

    boolean hasApiConfig = cfg != null
        && isNotBlank(cfg.getAccessKey())
        && isNotBlank(cfg.getAccessSecret())
        && isNotBlank(cfg.getSignature())
        && isNotBlank(cfg.getTemplateCode());

    boolean mockMode = debug || !hasApiConfig;

    log.setCreatedAt(LocalDateTime.now());

    if (mockMode) {
      // Mock mode: no third-party call, but keep full trace for operations.
      if (!hasApiConfig) {
        logg.info("[短信模拟发送] 未配置短信API参数，自动转为模拟发送。手机号: {}，内容: {}", log.getPhone(), log.getContent());
      } else {
        logg.info("[短信模拟发送] 调试模式开启，手机号: {}，内容: {}", log.getPhone(), log.getContent());
      }
      if (cfg != null) {
        logg.debug("SMS config (masked): provider={}, signature={}, templateCode={}, debugMode={}", cfg.getProvider(),
            cfg.getSignature(), cfg.getTemplateCode(), cfg.getDebugMode());
      }
      log.setStatus("success");
      log.setIsDebug(1);
      remindLogRepository.save(log);
      createSystemMessage(log, true, true, null);
      return log;
    }

    // Real sending placeholder: here we would call Aliyun SDK. Currently log and
    // simulate.
    try {
      if (cfg == null) {
        logg.warn("No SmsConfig found; cannot send via Aliyun. Marking failed.");
        log.setStatus("failed");
        log.setFailReason("no sms config");
        log.setIsDebug(0);
        remindLogRepository.save(log);
        createSystemMessage(log, false, false, "no sms config");
        return log;
      }

      // Log the intent and masked secret
      logg.info("[SMS SEND] Sending to {} content='{}' using provider={} signature={} template={}", log.getPhone(),
          log.getContent(), cfg.getProvider(), cfg.getSignature(), cfg.getTemplateCode());
      logg.debug("AccessKey={}, accessSecret(masked)={}, debugMode={}", cfg.getAccessKey(),
          cfg.getAccessSecret() == null ? null : "****masked****", cfg.getDebugMode());

      // TODO: Integrate Aliyun SMS SDK here using
      // cfg.getAccessKey()/getAccessSecret()/getTemplateCode()/getSignature()
      // Example (pseudo):
      // DefaultProfile profile = DefaultProfile.getProfile(region,
      // cfg.getAccessKey(), cfg.getAccessSecret());
      // CommonRequest request = new CommonRequest(); ...
      // client.getCommonResponse(request);

      // Simulate success
      log.setStatus("success");
      log.setIsDebug(0);
      remindLogRepository.save(log);
      logg.info("[SMS SEND] Simulated successful send to {}", log.getPhone());
      createSystemMessage(log, true, false, null);
      return log;
    } catch (Exception ex) {
      logg.error("[SMS SEND] Error sending sms: {}", ex.getMessage(), ex);
      log.setStatus("failed");
      log.setFailReason(ex.getMessage());
      log.setIsDebug(0);
      remindLogRepository.save(log);
      createSystemMessage(log, false, false, ex.getMessage());
      return log;
    }
  }

  private void createSystemMessage(RemindLog log, boolean success, boolean mock, String reason) {
    SystemMessage msg = new SystemMessage();
    msg.setType(mock ? "SMS_MOCK" : "SMS_NOTIFY");
    msg.setTitle(success ? (mock ? "短信已发送(模拟)" : "短信已发送") : "短信发送失败");

    StringBuilder content = new StringBuilder();
    content.append("租户: ").append(log.getTenantName() == null ? "-" : log.getTenantName())
        .append("; 手机号: ").append(log.getPhone() == null ? "-" : log.getPhone())
        .append("; 房间: ").append(log.getRoomNumber() == null ? "-" : log.getRoomNumber())
        .append("; 内容: ").append(log.getContent() == null ? "" : log.getContent());
    if (!success && reason != null && !reason.isBlank()) {
      content.append("; 失败原因: ").append(reason);
    }

    msg.setContent(content.toString());
    msg.setRelatedId(log.getTenantId());
    msg.setIsRead(false);
    msg.setCreatedAt(LocalDateTime.now());
    systemMessageRepository.save(msg);
  }

  private boolean isNotBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
