-- 房屋租赁管理系统 - 数据备份与恢复功能

USE house_db;

-- 备份记录表
CREATE TABLE IF NOT EXISTS `backup_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '备份ID',
  `backup_time` datetime NOT NULL COMMENT '备份时间',
  `type` varchar(10) NOT NULL DEFAULT 'manual' COMMENT '备份类型: auto/manual',
  `status` varchar(10) NOT NULL DEFAULT 'success' COMMENT '状态: success/failed',
  `file_name` varchar(255) NOT NULL COMMENT '备份文件名',
  `file_path` varchar(500) NOT NULL COMMENT '存储路径',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
  `md5` varchar(32) COMMENT '文件MD5',
  `fail_reason` text COMMENT '失败原因',
  `operator_id` bigint COMMENT '操作人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_backup_time` (`backup_time`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份记录表';

-- 恢复日志表
CREATE TABLE IF NOT EXISTS `restore_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '恢复ID',
  `restore_time` datetime NOT NULL COMMENT '恢复时间',
  `backup_id` bigint NOT NULL COMMENT '关联的备份ID',
  `operator_id` bigint NOT NULL COMMENT '操作用户ID',
  `status` varchar(10) NOT NULL DEFAULT 'success' COMMENT '恢复状态: success/failed',
  `fail_reason` text COMMENT '失败原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_backup_id` (`backup_id`),
  KEY `idx_restore_time` (`restore_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='恢复日志表';

-- 备份配置表
CREATE TABLE IF NOT EXISTS `backup_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用自动备份',
  `backup_type` varchar(10) DEFAULT 'daily' COMMENT '备份周期: daily/weekly',
  `backup_day` int DEFAULT 1 COMMENT '每周备份日(1-7, 1=周一)',
  `backup_time` varchar(10) DEFAULT '02:00' COMMENT '备份时间(HH:mm)',
  `retention_count` int DEFAULT 7 COMMENT '保留份数',
  `storage_path` varchar(500) DEFAULT './backups' COMMENT '存储路径',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份配置表';

-- 初始化默认配置
INSERT INTO `backup_config` (`enabled`, `backup_type`, `backup_day`, `backup_time`, `retention_count`, `storage_path`)
SELECT 0, 'daily', 1, '02:00', 7, './backups'
WHERE NOT EXISTS (SELECT 1 FROM `backup_config` WHERE `id` = 1);

-- 维护模式表
CREATE TABLE IF NOT EXISTS `maintenance_mode` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `enabled` tinyint(1) DEFAULT 0 COMMENT '是否启用维护模式',
  `reason` varchar(255) COMMENT '维护原因',
  `operator_id` bigint COMMENT '操作人ID',
  `started_at` datetime COMMENT '开始时间',
  `ended_at` datetime COMMENT '结束时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维护模式表';
