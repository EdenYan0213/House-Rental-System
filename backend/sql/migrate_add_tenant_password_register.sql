USE house_db;
SET @db = DATABASE();

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'password'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `password` varchar(255) NULL COMMENT '租客登录密码(BCrypt)' AFTER `phone`",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'register_source'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `register_source` varchar(20) NOT NULL DEFAULT 'ADMIN' COMMENT '注册来源: ADMIN/APP' AFTER `remark`",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `tenant`
SET `register_source` = 'ADMIN'
WHERE `register_source` IS NULL OR `register_source` = '';
