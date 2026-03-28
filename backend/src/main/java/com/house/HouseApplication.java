package com.house;

import com.house.service.UserService;
import com.house.repo.SmsConfigRepository;
import com.house.repo.RemindRuleRepository;
import com.house.model.SmsConfig;
import com.house.model.RemindRule;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.house.service.RemindService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EntityScan(basePackages = "com.house.model")
@EnableScheduling
@EnableAsync
public class HouseApplication {
  public static void main(String[] args) {
    SpringApplication.run(HouseApplication.class, args);
  }

  @Bean
  public CommandLineRunner init(UserService userService) {
    return args -> {
      try {
        userService.initDefaultAdmin();
      } catch (Exception e) {
        // ignore
      }
    };
  }

  @Bean
  public CommandLineRunner seedRemindData(SmsConfigRepository smsConfigRepository,
      RemindRuleRepository remindRuleRepository, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    return args -> {
      try {
        // Ensure tables exist (simple migration fallback for dev)
        try {
          jdbcTemplate.queryForObject("select 1 from remind_rule limit 1", Integer.class);
        } catch (Exception ex) {
          // create tables if missing
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS remind_rule (id INT AUTO_INCREMENT PRIMARY KEY, overdue_days INT NOT NULL, send_time VARCHAR(5) NOT NULL DEFAULT '09:00', sms_template TEXT, is_enabled TINYINT(1) DEFAULT 0, sort_order INT DEFAULT 0)");
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS sms_config (id INT AUTO_INCREMENT PRIMARY KEY, provider VARCHAR(50) DEFAULT 'aliyun', access_key VARCHAR(255), access_secret VARCHAR(255), signature VARCHAR(50), template_code VARCHAR(100), debug_mode TINYINT(1) DEFAULT 1, status TINYINT(1) DEFAULT 1)");
          jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS remind_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id INT, tenant_name VARCHAR(50), phone VARCHAR(20), room_number VARCHAR(50), overdue_days INT, bill_ids TEXT, total_amount DECIMAL(10,2), content TEXT, status ENUM('success','failed') DEFAULT 'success', fail_reason TEXT, is_debug TINYINT(1) DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }

        if (smsConfigRepository.count() == 0) {
          SmsConfig cfg = SmsConfig.builder().provider("aliyun").accessKey("").accessSecret("").signature("租赁平台")
              .templateCode("").debugMode(1).status(1).build();
          smsConfigRepository.save(cfg);
        }
        if (remindRuleRepository.count() == 0) {
          String hhmm = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
          RemindRule rule = RemindRule.builder().overdueDays(1).sendTime(hhmm)
              .smsTemplate("尊敬的{租户姓名}，您房间{房间号}有{逾期笔数}笔账单逾期，总欠费{总欠费金额}元，请尽快处理。").isEnabled(1).sortOrder(0).build();
          remindRuleRepository.save(rule);
        }
      } catch (Exception e) {
        // ignore seed errors
      }
    };
  }

  @Bean
  public CommandLineRunner triggerRemindOnce(RemindService remindService) {
    return args -> {
      // schedule a one-time trigger after 5 seconds to allow the app to finish
      // startup
      Executors.newSingleThreadScheduledExecutor().schedule(() -> {
        try {
          remindService.checkAndSendReminds();
        } catch (Exception e) {
          // ignore
        }
      }, 5, TimeUnit.SECONDS);
    };
  }
}
