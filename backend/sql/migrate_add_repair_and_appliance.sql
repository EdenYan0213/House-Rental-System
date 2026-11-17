USE house_db;

CREATE TABLE IF NOT EXISTS `appliance_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `default_quantity` int DEFAULT 1,
  `unit` varchar(20) DEFAULT '个',
  `sort_order` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `room_appliance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `appliance_name` varchar(100) NOT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `quantity` int DEFAULT 1,
  `unit` varchar(20) DEFAULT '个',
  `status` varchar(20) DEFAULT 'NORMAL',
  `remark` text,
  `photos` json DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_appliance_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `repair_worker` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT 1,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_repair_worker_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `repair_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(50) NOT NULL,
  `room_id` bigint NOT NULL,
  `tenant_id` bigint DEFAULT NULL,
  `reporter_type` varchar(20) DEFAULT 'admin',
  `reported_by` bigint DEFAULT NULL,
  `fault_type` varchar(50) NOT NULL,
  `description` text,
  `images` json DEFAULT NULL,
  `appliance_id` bigint DEFAULT NULL,
  `status` varchar(30) DEFAULT 'pending',
  `worker_id` bigint DEFAULT NULL,
  `assigned_at` datetime DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `finish_time` datetime DEFAULT NULL,
  `accept_time` datetime DEFAULT NULL,
  `cancel_reason` text,
  `repair_result` text,
  `material_fee` decimal(10,2) DEFAULT 0,
  `labor_fee` decimal(10,2) DEFAULT 0,
  `total_fee` decimal(10,2) DEFAULT 0,
  `fee_remark` text,
  `is_tenant_charge` tinyint DEFAULT 0,
  `bill_item_id` bigint DEFAULT NULL,
  `rating` tinyint DEFAULT NULL,
  `comment` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_repair_order_no` (`order_no`),
  KEY `idx_repair_order_status` (`status`),
  KEY `idx_repair_order_room` (`room_id`),
  KEY `idx_repair_order_worker` (`worker_id`),
  KEY `idx_repair_order_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO appliance_template (`name`, `brand`, `default_quantity`, `unit`, `sort_order`)
SELECT '床', '', 1, '张', 1 WHERE NOT EXISTS (SELECT 1 FROM appliance_template WHERE name = '床');
INSERT INTO appliance_template (`name`, `brand`, `default_quantity`, `unit`, `sort_order`)
SELECT '衣柜', '', 1, '个', 2 WHERE NOT EXISTS (SELECT 1 FROM appliance_template WHERE name = '衣柜');
INSERT INTO appliance_template (`name`, `brand`, `default_quantity`, `unit`, `sort_order`)
SELECT '空调', '', 1, '台', 3 WHERE NOT EXISTS (SELECT 1 FROM appliance_template WHERE name = '空调');
INSERT INTO appliance_template (`name`, `brand`, `default_quantity`, `unit`, `sort_order`)
SELECT '热水器', '', 1, '台', 4 WHERE NOT EXISTS (SELECT 1 FROM appliance_template WHERE name = '热水器');
