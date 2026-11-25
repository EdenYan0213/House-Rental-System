package com.house;

import com.house.model.RemindRule;
import com.house.model.SmsConfig;
import com.house.repo.RemindRuleRepository;
import com.house.repo.SmsConfigRepository;
import com.house.service.RemindService;
import com.house.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 应用入口与启动期初始化。
 *
 * <p>启动期的种子数据失败不应该阻止服务启动（否则数据库未建表时完全起不来），
 * 但**必须留下日志**：历史上这里的 `catch (Exception e) { // ignore }` 曾导致
 * 「schema 变更后 admin 登录失败，日志里查不到任何原因」。
 */
@SpringBootApplication
@EntityScan(basePackages = "com.house.model")
@EnableScheduling
@EnableAsync
public class HouseApplication implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(HouseApplication.class);

  private final ScheduledExecutorService startupExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "startup-remind");
        t.setDaemon(true);
        return t;
      });

  public static void main(String[] args) {
    SpringApplication.run(HouseApplication.class, args);
  }

  @Bean
  public CommandLineRunner init(UserService userService) {
    return args -> {
      try {
        userService.initDefaultAdmin();
      } catch (Exception e) {
        // 种子管理员失败通常意味着 schema 与实体不一致，
        // 打完整堆栈，避免「登录失败但日志无痕」。
        log.error("初始化默认管理员失败，登录可能不可用；请检查数据库 schema 是否与实体一致", e);
      }
    };
  }

  @Bean
  public CommandLineRunner seedRemindData(SmsConfigRepository smsConfigRepository,
      RemindRuleRepository remindRuleRepository,
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    return args -> {
      try {
        // 兜底建表：仅用于本地开发，生产应由 migrate_*.sql 管理
        try {
          jdbcTemplate.queryForObject("select 1 from remind_rule limit 1", Integer.class);
        } catch (Exception ex) {
          log.warn("remind_rule 不存在，尝试自动建表（仅建议用于开发环境）: {}", ex.getMessage());
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS remind_rule (id INT AUTO_INCREMENT PRIMARY KEY, overdue_days INT NOT NULL, send_time VARCHAR(5) NOT NULL DEFAULT '09:00', sms_template TEXT, is_enabled TINYINT(1) DEFAULT 0, sort_order INT DEFAULT 0)");
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS sms_config (id INT AUTO_INCREMENT PRIMARY KEY, provider VARCHAR(50) DEFAULT 'aliyun', access_key VARCHAR(255), access_secret VARCHAR(255), signature VARCHAR(50), template_code VARCHAR(100), debug_mode TINYINT(1) DEFAULT 1, status TINYINT(1) DEFAULT 1)");
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS remind_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id INT, tenant_name VARCHAR(50), phone VARCHAR(20), room_number VARCHAR(50), overdue_days INT, bill_ids TEXT, total_amount DECIMAL(10,2), content TEXT, status ENUM('success','failed') DEFAULT 'success', fail_reason TEXT, is_debug TINYINT(1) DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }

        if (smsConfigRepository.count() == 0) {
          SmsConfig cfg = SmsConfig.builder()
              .provider("aliyun")
              .accessKey("")
              .accessSecret("")
              .signature("租赁平台")
              .templateCode("")
              .debugMode(1)
              .status(1)
              .build();
          smsConfigRepository.save(cfg);
          log.info("已初始化默认短信配置（调试模式）");
        }
        if (remindRuleRepository.count() == 0) {
          String hhmm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
          RemindRule rule = RemindRule.builder()
              .overdueDays(1)
              .sendTime(hhmm)
              .smsTemplate("尊敬的{租户姓名}，您房间{房间号}有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元，请尽快处理。")
              .isEnabled(1)
              .sortOrder(0)
              .build();
          remindRuleRepository.save(rule);
          log.info("已初始化默认催缴规则");
        }
      } catch (Exception e) {
        log.error("初始化催缴/短信默认数据失败，相关页面可能缺少初始配置", e);
      }
    };
  }

  @Bean
  public CommandLineRunner triggerRemindOnce(RemindService remindService) {
    return args -> {
      // 启动后 5 秒触发一次催缴检查，给应用留出启动时间
      startupExecutor.schedule(() -> {
        try {
          remindService.checkAndSendReminds();
        } catch (Exception e) {
          // 启动期催缴失败不影响主流程，但需要可见
          log.warn("启动后首次催缴检查失败: {}", e.getMessage(), e);
        }
      }, 5, TimeUnit.SECONDS);
    };
  }

  /** 关闭时释放启动期线程池，避免测试/热重启时残留非守护线程。 */
  @Override
  public void destroy() {
    startupExecutor.shutdownNow();
  }
}
