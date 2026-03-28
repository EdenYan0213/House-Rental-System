-- Tenant portal phase 3: e-sign, message center, dashboard, i18n preferences

USE house_db;

-- tenant preference fields
SET @db = DATABASE();

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'language_preference'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `language_preference` varchar(20) NOT NULL DEFAULT 'zh-CN' COMMENT '语言偏好: zh-CN/en-US'",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'notify_overdue'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `notify_overdue` tinyint NOT NULL DEFAULT 1 COMMENT '逾期提醒开关'",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'notify_repair'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `notify_repair` tinyint NOT NULL DEFAULT 1 COMMENT '报修投诉进度提醒开关'",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'notify_contract'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `notify_contract` tinyint NOT NULL DEFAULT 1 COMMENT '合同提醒开关'",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'notify_notice'
);
SET @sql = IF(@exists = 0,
  "ALTER TABLE `tenant` ADD COLUMN `notify_notice` tinyint NOT NULL DEFAULT 1 COMMENT '公告提醒开关'",
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- tenant message center table
CREATE TABLE IF NOT EXISTS `tenant_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `room_id` bigint DEFAULT NULL,
  `category` varchar(20) NOT NULL COMMENT 'TODO/NOTICE',
  `type` varchar(50) NOT NULL COMMENT 'OVERDUE/REPAIR_PROGRESS/CONTRACT_EXPIRE/NOTICE_NEW/SIGN_RETRY',
  `title` varchar(120) NOT NULL,
  `content` text NOT NULL,
  `target_url` varchar(300) DEFAULT NULL,
  `ref_type` varchar(50) DEFAULT NULL,
  `ref_id` bigint DEFAULT NULL,
  `is_read` tinyint NOT NULL DEFAULT 0,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  `need_action` tinyint NOT NULL DEFAULT 0,
  `action_status` varchar(20) NOT NULL DEFAULT 'DONE' COMMENT 'PENDING/DONE',
  `created_at` datetime NOT NULL,
  `read_at` datetime DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_message_tenant` (`tenant_id`),
  KEY `idx_tenant_message_category` (`category`),
  KEY `idx_tenant_message_ref` (`ref_type`, `ref_id`),
  KEY `idx_tenant_message_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- sign agreement table
CREATE TABLE IF NOT EXISTS `sign_agreement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `verify_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `verify_name` varchar(50) DEFAULT NULL,
  `verify_id_card` varchar(50) DEFAULT NULL,
  `sign_status` varchar(20) NOT NULL DEFAULT 'UNSIGNED',
  `sign_provider` varchar(50) DEFAULT NULL,
  `sign_flow_no` varchar(100) DEFAULT NULL,
  `signed_at` datetime DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `last_error` text,
  `retry_count` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sign_contract_tenant` (`contract_id`, `tenant_id`),
  KEY `idx_sign_flow_no` (`sign_flow_no`),
  KEY `idx_sign_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
