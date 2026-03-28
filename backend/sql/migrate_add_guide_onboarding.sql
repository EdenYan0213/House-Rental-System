-- Onboarding guide fields and dismiss table

USE house_db;

SET @db = DATABASE();

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'first_login'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `sys_user` ADD COLUMN `first_login` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否首次登录引导' AFTER `theme_preference`",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_context_tips'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `sys_user` ADD COLUMN `show_context_tips` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否显示上下文提示' AFTER `first_login`",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_task_card'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `sys_user` ADD COLUMN `show_task_card` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否显示新手任务卡' AFTER `show_context_tips`",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `guide_dismiss` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dismiss_key` varchar(100) NOT NULL,
  `dismissed_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guide_user_key` (`user_id`, `dismiss_key`),
  KEY `idx_guide_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
