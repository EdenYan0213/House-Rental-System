USE house_db;

-- 幂等：sys_user 可能已由 init_db.sql 建好该列，重复 ALTER 会报 1060
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'theme_preference') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `theme_preference` varchar(20) NOT NULL DEFAULT ''system'' COMMENT ''主题偏好: light/dark/system'' AFTER `last_login_time`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
