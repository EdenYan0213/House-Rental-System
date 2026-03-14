package com.house.config;

import com.house.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Configuration
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    @Autowired
    private BackupService backupService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledBackup() {
        try {
            var config = backupService.getConfig();
            if (config.getEnabled() == null || !config.getEnabled()) {
                return;
            }

            String backupType = config.getBackupType();
            if ("daily".equals(backupType)) {
                logger.info("执行每日自动备份");
                backupService.createBackup("auto", null);
            } else if ("weekly".equals(backupType)) {
                int backupDay = config.getBackupDay() != null ? config.getBackupDay() : 1;
                int currentDayOfWeek = LocalDate.now().getDayOfWeek().getValue();
                if (currentDayOfWeek == backupDay) {
                    logger.info("执行每周自动备份");
                    backupService.createBackup("auto", null);
                }
            }
        } catch (Exception e) {
            logger.error("自动备份任务执行失败", e);
        }
    }
}
