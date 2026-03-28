package com.house.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class OperationLogLifecycleService {
  private final JdbcTemplate jdbcTemplate;

  public OperationLogLifecycleService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Scheduled(cron = "${app.log.lifecycle-cron:0 10 2 * * *}")
  @Transactional
  public void archiveAndCleanupDaily() {
    archiveAndCleanup();
  }

  @Transactional
  public Map<String, Object> archiveAndCleanup() {
    Integer archived = jdbcTemplate.queryForObject("""
        insert into operation_log_archive (
          id, user_id, username, operation_time, operator, module, action, business_id, business_name,
          detail_before, detail_after, change_summary, request_ip, user_agent, request_params,
          result, fail_reason, duration_ms, log_level, ip, created_at,
          ai_instruction, ai_intent, ai_entities, ai_status, ai_response_message, ai_duration_ms, ai_model_name
        )
        select
          id, user_id, username, operation_time, operator, module, action, business_id, business_name,
          detail_before, detail_after, change_summary, request_ip, user_agent, request_params,
          result, fail_reason, duration_ms, log_level, ip, created_at,
          ai_instruction, ai_intent, ai_entities, ai_status, ai_response_message, ai_duration_ms, ai_model_name
        from operation_log
        where operation_time < date_sub(now(), interval 3 month)
          and not exists (select 1 from operation_log_archive a where a.id = operation_log.id)
        """, Integer.class);

    int removedOnline = jdbcTemplate.update("""
        delete from operation_log
        where operation_time < date_sub(now(), interval 3 month)
        """);

    int removedArchive = jdbcTemplate.update("""
        delete from operation_log_archive
        where operation_time < date_sub(now(), interval 1 year)
        """);

    Map<String, Object> result = new HashMap<>();
    result.put("archived", archived == null ? 0 : archived);
    result.put("removedOnline", removedOnline);
    result.put("removedArchive", removedArchive);
    return result;
  }
}
