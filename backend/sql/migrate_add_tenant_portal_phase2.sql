-- Tenant portal phase 2: repair/complaint self-service, contract renew, feedback, profile enhance

USE house_db;

ALTER TABLE `repair_order`
  ADD COLUMN IF NOT EXISTS `expected_time` datetime NULL COMMENT '租客期望维修时间',
  ADD COLUMN IF NOT EXISTS `contact_phone` varchar(20) NULL COMMENT '租客联系电话';

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
