-- 房屋租赁管理系统数据库初始化脚本

USE house_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `real_name` varchar(50) COMMENT '真实姓名',
  `phone` varchar(20) COMMENT '手机号',
  `email` varchar(100) COMMENT '邮箱',
  `role` varchar(20) DEFAULT 'USER' COMMENT '角色: SUPER_ADMIN/ADMIN/USER',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_time` datetime COMMENT '最后登录时间',
  `theme_preference` varchar(20) NOT NULL DEFAULT 'system' COMMENT '主题偏好: light/dark/system',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 初始化超级管理员用户 (密码: admin123)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`, `enabled`)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'SUPER_ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'admin');

-- 楼栋表
CREATE TABLE IF NOT EXISTS `building` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '楼栋ID',
  `name` varchar(100) NOT NULL COMMENT '楼栋名称',
  `code` varchar(50) COMMENT '楼栋编号',
  `address` varchar(200) COMMENT '地址',
  `image_url` varchar(500) COMMENT '楼栋图片URL',
  `total_floors` int DEFAULT 1 COMMENT '楼层数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='楼栋表';

-- 房间表
CREATE TABLE IF NOT EXISTS `room` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '房间ID',
  `building_id` bigint NOT NULL COMMENT '楼栋ID',
  `floor` int COMMENT '楼层',
  `room_number` varchar(50) NOT NULL COMMENT '房间号',
  `layout` varchar(50) COMMENT '户型',
  `area` decimal(10,2) COMMENT '面积(平方米)',
  `status` varchar(20) DEFAULT 'IDLE' COMMENT '状态: IDLE/RENTED/MAINTENANCE',
  `water_meter_id` bigint COMMENT '水表ID',
  `electric_meter_id` bigint COMMENT '电表ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_building_floor_number` (`building_id`, `floor`, `room_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房间表';

-- 租户表
CREATE TABLE IF NOT EXISTS `tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `phone` varchar(20) NOT NULL COMMENT '手机号',
  `id_card` varchar(18) COMMENT '身份证号',
  `emergency_contact` varchar(50) COMMENT '紧急联系人',
  `emergency_phone` varchar(20) COMMENT '紧急联系电话',
  `remark` text COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- 合同表
CREATE TABLE IF NOT EXISTS `contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `contract_no` varchar(50) NOT NULL COMMENT '合同编号',
  `room_id` bigint NOT NULL COMMENT '房间ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `start_date` date NOT NULL COMMENT '起租日期',
  `end_date` date NOT NULL COMMENT '结束日期',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRED/TERMINATED/PENDING',
  `monthly_rent` decimal(12,2) COMMENT '月租金',
  `deposit` decimal(12,2) COMMENT '押金',
  `payment_method` varchar(20) DEFAULT 'MONTHLY' COMMENT '付款方式',
  `remark` text COMMENT '备注',
  `attachment_url` varchar(500) COMMENT '附件URL',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同表';

-- 费用项表
CREATE TABLE IF NOT EXISTS `fee_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '费用项ID',
  `name` varchar(100) NOT NULL COMMENT '费用名称',
  `unit_price` decimal(10,2) NOT NULL COMMENT '单价',
  `unit` varchar(20) DEFAULT '元/月' COMMENT '单位',
  `category` varchar(20) DEFAULT 'BASE' COMMENT '类别',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费用项表';

-- 水电读数表
CREATE TABLE IF NOT EXISTS `meter_reading` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '读数ID',
  `room_id` bigint NOT NULL COMMENT '房间ID',
  `record_date` date NOT NULL COMMENT '录入日期',
  `water_reading` decimal(10,2) DEFAULT 0 COMMENT '水表读数',
  `electric_reading` decimal(10,2) DEFAULT 0 COMMENT '电表读数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_date` (`room_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='水电读数表';

-- 账单表
CREATE TABLE IF NOT EXISTS `bill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账单ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `bill_month` varchar(7) NOT NULL COMMENT '账单月份 yyyy-MM',
  `total_amount` decimal(12,2) DEFAULT 0 COMMENT '总金额',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/OBSOLETE',
  `generated_date` datetime COMMENT '生成日期',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_month` (`contract_id`, `bill_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单表';

-- 账单明细表
CREATE TABLE IF NOT EXISTS `bill_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `bill_id` bigint NOT NULL COMMENT '账单ID',
  `fee_item_id` bigint NOT NULL COMMENT '费用项ID',
  `usage` decimal(10,2) COMMENT '用量',
  `amount` decimal(12,2) COMMENT '金额',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_bill_id` (`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单明细表';

-- 押金记录表
CREATE TABLE IF NOT EXISTS `deposit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '押金ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `type` varchar(20) NOT NULL COMMENT '类型: RECEIVE/REFUND',
  `amount` decimal(12,2) NOT NULL COMMENT '金额',
  `date` date NOT NULL COMMENT '日期',
  `remark` text COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='押金记录表';

-- 收款记录表
CREATE TABLE IF NOT EXISTS `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收款ID',
  `bill_id` bigint NOT NULL COMMENT '账单ID',
  `paid_amount` decimal(12,2) NOT NULL COMMENT '收款金额',
  `paid_date` datetime COMMENT '收款日期',
  `payment_method` varchar(50) COMMENT '支付方式',
  `remark` text COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_bill_id` (`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收款记录表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operation_time` datetime NOT NULL COMMENT '操作时间',
  `operator` varchar(50) NOT NULL COMMENT '操作人',
  `module` varchar(50) COMMENT '模块',
  `action` varchar(200) COMMENT '操作内容',
  `ip` varchar(50) COMMENT 'IP地址',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 器具模板表
CREATE TABLE IF NOT EXISTS `appliance_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `default_quantity` int DEFAULT 1,
  `unit` varchar(20) DEFAULT '个',
  `sort_order` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='器具模板';

-- 房间器具表
CREATE TABLE IF NOT EXISTS `room_appliance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `appliance_name` varchar(100) NOT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `quantity` int DEFAULT 1,
  `unit` varchar(20) DEFAULT '个',
  `status` varchar(20) DEFAULT '正常',
  `remark` text,
  `photos` json DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_appliance_room_id` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房间器具';

-- 维修工表
CREATE TABLE IF NOT EXISTS `repair_worker` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT 1,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_repair_worker_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修工';

-- 维修工单表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修工单';
