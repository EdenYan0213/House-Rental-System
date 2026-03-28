package com.house.service;

import com.house.model.AlertRecord;
import com.house.model.AlertRule;
import com.house.repo.AlertRecordRepository;
import com.house.repo.AlertRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AlertService {
  private final AlertRuleRepository alertRuleRepository;
  private final AlertRecordRepository alertRecordRepository;
  private final JdbcTemplate jdbcTemplate;

  public AlertService(AlertRuleRepository alertRuleRepository,
      AlertRecordRepository alertRecordRepository,
      JdbcTemplate jdbcTemplate) {
    this.alertRuleRepository = alertRuleRepository;
    this.alertRecordRepository = alertRecordRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<AlertRule> listRules() {
    return alertRuleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
  }

  @Transactional
  public AlertRule saveRule(AlertRule rule) {
    LocalDateTime now = LocalDateTime.now();
    if (rule.getId() == null) {
      rule.setCreatedAt(now);
    }
    rule.setUpdatedAt(now);
    return alertRuleRepository.save(rule);
  }

  @Transactional
  public void deleteRule(Integer id) {
    alertRuleRepository.deleteById(id);
  }

  public Page<AlertRecord> searchRecords(String alertType, Integer status, int page, int size) {
    Specification<AlertRecord> spec = (root, query, cb) -> cb.conjunction();
    if (alertType != null && !alertType.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("alertType"), alertType));
    }
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    return alertRecordRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  @Transactional
  public AlertRecord markHandled(Integer id, Integer operatorId) {
    AlertRecord record = alertRecordRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("预警不存在"));
    record.setStatus(1);
    record.setOperatorId(operatorId);
    record.setHandledAt(LocalDateTime.now());
    return alertRecordRepository.save(record);
  }

  @Scheduled(cron = "${app.alert.scan-cron:0 */1 * * * *}")
  @Transactional
  public void evaluateRules() {
    List<AlertRule> rules = alertRuleRepository.findByEnabled(1);
    for (AlertRule rule : rules) {
      String type = rule.getRuleType();
      if ("high_frequency".equals(type)) {
        evaluateHighFrequency(rule);
      } else if ("login_failures".equals(type)) {
        evaluateLoginFailures(rule);
      } else if ("sensitive_operation".equals(type)) {
        evaluateSensitiveOps(rule);
      }
    }
  }

  private void evaluateHighFrequency(AlertRule rule) {
    int threshold = rule.getThresholdValue() == null ? 5 : rule.getThresholdValue();
    int window = rule.getWindowMinutes() == null ? 1 : rule.getWindowMinutes();
    String sql = """
        select coalesce(username, operator) as op, action, count(*) as c
        from operation_log
        where operation_time >= date_sub(now(), interval ? minute)
        group by op, action
        having c >= ?
        """;
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, window, threshold);
    for (Map<String, Object> row : rows) {
      String title = "短时高频操作: " + row.get("op") + " / " + row.get("action");
      String content = "在" + window + "分钟内执行次数=" + row.get("c") + "，超过阈值=" + threshold;
      createAlertIfNotDuplicate("high_frequency", title, content, rule.getLevel());
    }
  }

  private void evaluateLoginFailures(AlertRule rule) {
    int threshold = rule.getThresholdValue() == null ? 3 : rule.getThresholdValue();
    int window = rule.getWindowMinutes() == null ? 5 : rule.getWindowMinutes();
    String sql = """
        select coalesce(username, operator) as op, count(*) as c
        from operation_log
        where operation_time >= date_sub(now(), interval ? minute)
          and action = 'login'
          and result = 0
        group by op
        having c >= ?
        """;
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, window, threshold);
    for (Map<String, Object> row : rows) {
      String title = "连续登录失败: " + row.get("op");
      String content = "在" + window + "分钟内登录失败=" + row.get("c") + "次，超过阈值=" + threshold;
      createAlertIfNotDuplicate("login_failures", title, content, rule.getLevel());
    }
  }

  private void evaluateSensitiveOps(AlertRule rule) {
    int window = rule.getWindowMinutes() == null ? 10 : rule.getWindowMinutes();
    String sql = """
        select coalesce(username, operator) as op, module, action, business_id, business_name, operation_time
        from operation_log
        where operation_time >= date_sub(now(), interval ? minute)
          and log_level = 2
        order by operation_time desc
        limit 20
        """;
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, window);
    for (Map<String, Object> row : rows) {
      String title = "敏感操作: " + row.get("op") + " / " + row.get("action");
      String content = "模块=" + row.get("module") + ", 业务=" + row.get("business_name") + "(" + row.get("business_id")
          + ")";
      createAlertIfNotDuplicate("sensitive_operation", title, content, rule.getLevel());
    }
  }

  private void createAlertIfNotDuplicate(String type, String title, String content, Integer level) {
    LocalDateTime dedupeSince = LocalDateTime.now().minusMinutes(5);
    if (alertRecordRepository.existsByAlertTypeAndTitleAndCreatedAtAfter(type, title, dedupeSince)) {
      return;
    }
    AlertRecord record = new AlertRecord();
    record.setAlertType(type);
    record.setTitle(title);
    record.setContent(content);
    record.setLevel(level == null ? 2 : level);
    record.setStatus(0);
    record.setCreatedAt(LocalDateTime.now());
    alertRecordRepository.save(record);
  }
}
