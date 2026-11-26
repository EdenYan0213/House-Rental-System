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
  `first_login` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否首次登录引导',
  `show_context_tips` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否显示上下文提示',
  `show_task_card` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否显示新手任务卡',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 超级管理员由应用首次启动时播种（UserService.initDefaultAdmin），
-- 密码取自环境变量 ADMIN_INIT_PASSWORD；未设置则回退开发默认 admin123 并告警。
--
-- 此处刻意不再插入固定哈希的 admin 记录：历史版本预置的哈希对应一个未知密码，
-- 会让 ADMIN_INIT_PASSWORD 因「账号已存在」被跳过，最终把人锁在账号外。

-- 新手引导关闭记录表
CREATE TABLE IF NOT EXISTS `guide_dismiss` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dismiss_key` varchar(100) NOT NULL,
  `dismissed_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guide_user_key` (`user_id`, `dismiss_key`),
  KEY `idx_guide_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='引导关闭记录表';

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
  `password` varchar(255) COMMENT '租客登录密码(BCrypt)',
  `id_card` varchar(18) COMMENT '身份证号',
  `emergency_contact` varchar(50) COMMENT '紧急联系人',
  `emergency_phone` varchar(20) COMMENT '紧急联系电话',
  `remark` text COMMENT '备注',
  `register_source` varchar(20) NOT NULL DEFAULT 'ADMIN' COMMENT '注册来源: ADMIN/APP',
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
  `category` varchar(20) DEFAULT 'OTHER' COMMENT '类别',
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
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `operation_time` datetime NOT NULL COMMENT '操作时间',
  `operator` varchar(50) NOT NULL COMMENT '操作人',
  `module` varchar(50) NOT NULL COMMENT '模块',
  `action` varchar(50) NOT NULL COMMENT '操作类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '业务对象ID',
  `business_name` varchar(255) DEFAULT NULL COMMENT '业务对象名称',
  `detail_before` json DEFAULT NULL COMMENT '操作前快照',
  `detail_after` json DEFAULT NULL COMMENT '操作后快照',
  `change_summary` text COMMENT '变更摘要',
  `request_ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
  `user_agent` varchar(255) DEFAULT NULL COMMENT 'UserAgent',
  `request_params` text COMMENT '请求参数（脱敏）',
  `result` tinyint NOT NULL DEFAULT 1 COMMENT '1成功0失败',
  `fail_reason` text COMMENT '失败原因',
  `duration_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  `log_level` tinyint NOT NULL DEFAULT 1 COMMENT '1-INFO 2-WARN 3-ERROR',
  `ip` varchar(50) COMMENT 'IP地址',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `ai_instruction` text COMMENT 'AI原始指令',
  `ai_intent` varchar(100) DEFAULT NULL COMMENT 'AI意图',
  `ai_entities` text COMMENT 'AI实体',
  `ai_status` varchar(50) DEFAULT NULL COMMENT 'AI执行状态',
  `ai_response_message` text COMMENT 'AI响应信息',
  `ai_duration_ms` bigint DEFAULT NULL COMMENT 'AI执行耗时',
  `ai_model_name` varchar(100) DEFAULT NULL COMMENT 'AI模型名',
  PRIMARY KEY (`id`)
  ,KEY `idx_log_created_module_action` (`created_at`,`module`,`action`)
  ,KEY `idx_log_business_id` (`business_id`)
  ,KEY `idx_log_user_time` (`username`,`operation_time`)
  ,KEY `idx_log_level_result_duration` (`log_level`,`result`,`duration_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

CREATE TABLE IF NOT EXISTS `alert_rule` (
  `id` int NOT NULL AUTO_INCREMENT,
  `rule_type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `threshold_value` int DEFAULT NULL,
  `window_minutes` int DEFAULT NULL,
  `level` tinyint NOT NULL DEFAULT 2,
  `notify_channels` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_alert_rule_type` (`rule_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警规则';

CREATE TABLE IF NOT EXISTS `alert_record` (
  `id` int NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `level` tinyint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `operator_id` int DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_alert_type_status_time` (`alert_type`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警记录';

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

-- 处理人表
CREATE TABLE IF NOT EXISTS `handler` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `role` varchar(50) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` tinyint DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_handler_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉处理人';

-- 投诉建议表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉建议';

-- 投诉处理记录表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投诉处理记录';

-- 管理员楼栋权限范围
CREATE TABLE IF NOT EXISTS `admin_building_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL,
  `building_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_building_scope` (`admin_id`,`building_id`),
  KEY `idx_admin_building_scope_admin_id` (`admin_id`),
  KEY `idx_admin_building_scope_building_id` (`building_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员楼栋可见范围';
