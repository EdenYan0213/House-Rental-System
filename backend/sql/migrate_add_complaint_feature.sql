USE house_db;

CREATE TABLE IF NOT EXISTS `handler` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `role` varchar(50) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_handler_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `complaint_suggestion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `complaint_no` varchar(50) NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `room_id` bigint NOT NULL,
  `reporter_type` varchar(20) DEFAULT 'admin',
  `reported_by` bigint DEFAULT NULL,
  `type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text,
  `images` json DEFAULT NULL,
  `is_anonymous` tinyint DEFAULT 0,
  `priority` varchar(20) DEFAULT 'normal',
  `status` varchar(30) DEFAULT 'pending',
  `assigned_to` bigint DEFAULT NULL,
  `assigned_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `closed_at` datetime DEFAULT NULL,
  `closed_reason` text,
  `rating` tinyint DEFAULT NULL,
  `comment` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_complaint_no` (`complaint_no`),
  KEY `idx_complaint_status` (`status`),
  KEY `idx_complaint_room_id` (`room_id`),
  KEY `idx_complaint_tenant_id` (`tenant_id`),
  KEY `idx_complaint_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `complaint_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `complaint_id` bigint NOT NULL,
  `operator_type` varchar(20) DEFAULT 'admin',
  `operator_id` bigint DEFAULT NULL,
  `action` varchar(50) NOT NULL,
  `content` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_complaint_log_complaint_id` (`complaint_id`),
  KEY `idx_complaint_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_building_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL,
  `building_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_building_scope` (`admin_id`,`building_id`),
  KEY `idx_admin_building_scope_admin_id` (`admin_id`),
  KEY `idx_admin_building_scope_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `handler` (`name`, `role`, `phone`, `status`)
SELECT '保洁主管', '保洁', '', 1
WHERE NOT EXISTS (SELECT 1 FROM `handler` WHERE `name` = '保洁主管');

INSERT INTO `handler` (`name`, `role`, `phone`, `status`)
SELECT '保安队长', '保安', '', 1
WHERE NOT EXISTS (SELECT 1 FROM `handler` WHERE `name` = '保安队长');
