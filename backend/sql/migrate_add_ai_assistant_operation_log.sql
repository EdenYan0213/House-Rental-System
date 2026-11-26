-- AI 助手操作日志字段
--
-- MySQL 8 兼容说明：原脚本用 `ADD COLUMN IF NOT EXISTS`（MariaDB 专有语法），
-- 在 MySQL 8 上会报 1064 导致整条 ALTER 失败。改为逐列判断 + 动态执行，幂等。

USE house_db;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_instruction') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_instruction` TEXT NULL COMMENT ''AI原始指令''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_intent') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_intent` varchar(50) NULL COMMENT ''AI解析意图''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_entities') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_entities` TEXT NULL COMMENT ''AI解析实体JSON''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_status') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_status` varchar(20) NULL COMMENT ''AI执行状态''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_response_message') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_response_message` TEXT NULL COMMENT ''AI返回消息''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_duration_ms') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_duration_ms` bigint NULL COMMENT ''AI执行耗时ms''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'ai_model_name') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `ai_model_name` varchar(100) NULL COMMENT ''AI模型名称''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
