USE house_db;

CREATE TABLE IF NOT EXISTS `operation_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(50) DEFAULT NULL,
  `module` varchar(50) NOT NULL,
  `operation` varchar(50) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `target_name` varchar(255) DEFAULT NULL,
  `table_name` varchar(50) NOT NULL,
  `before_data` json DEFAULT NULL,
  `after_data` json DEFAULT NULL,
  `is_recovered` tinyint(1) NOT NULL DEFAULT 0,
  `expired_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `recovered_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_created` (`created_at`),
  KEY `idx_snapshot_expired` (`expired_at`),
  KEY `idx_snapshot_target` (`table_name`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键操作快照';
