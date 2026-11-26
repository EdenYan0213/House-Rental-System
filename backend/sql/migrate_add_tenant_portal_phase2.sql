-- Tenant portal phase 2: repair/complaint self-service, contract renew, feedback, profile enhance
--
-- MySQL 8 兼容说明：原脚本用 `ADD COLUMN IF NOT EXISTS`，这是 MariaDB 专有语法。
-- 在 MySQL 8 上整条 ALTER 会报 1064，导致紧随其后的 CREATE TABLE 全部不执行
-- （表现为缺 renew_application / feedback 两张表、缺 repair_order 两个列）。
-- 现改为「查 information_schema + PREPARE 动态执行」，幂等且兼容 MySQL 8。

USE house_db;

-- repair_order.expected_time
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'repair_order'
               AND COLUMN_NAME = 'expected_time');
SET @sql := IF(@col = 0,
  'ALTER TABLE `repair_order` ADD COLUMN `expected_time` datetime NULL COMMENT ''租客期望维修时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- repair_order.contact_phone
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'repair_order'
               AND COLUMN_NAME = 'contact_phone');
SET @sql := IF(@col = 0,
  'ALTER TABLE `repair_order` ADD COLUMN `contact_phone` varchar(20) NULL COMMENT ''租客联系电话''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `renew_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `expected_duration` int NOT NULL COMMENT '期望续租月数',
  `remark` text,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已同意 2-已拒绝',
  `result_remark` text,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_renew_tenant` (`tenant_id`),
  KEY `idx_renew_contract` (`contract_id`),
  KEY `idx_renew_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL,
  `content` text NOT NULL,
  `images` text,
  `phone` varchar(20) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已处理',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_tenant` (`tenant_id`),
  KEY `idx_feedback_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
