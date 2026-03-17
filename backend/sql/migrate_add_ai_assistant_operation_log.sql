USE house_db;

ALTER TABLE `operation_log`
  ADD COLUMN IF NOT EXISTS `ai_instruction` TEXT NULL COMMENT 'AI原始指令',
  ADD COLUMN IF NOT EXISTS `ai_intent` varchar(50) NULL COMMENT 'AI解析意图',
  ADD COLUMN IF NOT EXISTS `ai_entities` TEXT NULL COMMENT 'AI解析实体JSON',
  ADD COLUMN IF NOT EXISTS `ai_status` varchar(20) NULL COMMENT 'AI执行状态',
  ADD COLUMN IF NOT EXISTS `ai_response_message` TEXT NULL COMMENT 'AI返回消息',
  ADD COLUMN IF NOT EXISTS `ai_duration_ms` bigint NULL COMMENT 'AI执行耗时ms',
  ADD COLUMN IF NOT EXISTS `ai_model_name` varchar(100) NULL COMMENT 'AI模型名称';
