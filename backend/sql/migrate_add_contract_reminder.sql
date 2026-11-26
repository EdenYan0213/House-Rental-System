-- 合同到期提醒功能 - 数据库结构变更
--
-- 从 contract_reminder.sql 改名为 migrate_* 形式，使其被
-- `for f in migrate_*.sql` 的部署流程覆盖（原文件名不在通配范围，
-- 导致 reminder_settings / system_message 两张表永远不会被创建）。
--
-- 同时补上幂等处理：原脚本的 ALTER ADD COLUMN 重复执行会报错，
-- INSERT 默认配置每次都会多插一条。

USE house_db;

-- 1. contract.reminder_sent
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'contract'
                  AND COLUMN_NAME = 'reminder_sent') = 0,
  'ALTER TABLE contract ADD COLUMN reminder_sent VARCHAR(500) DEFAULT NULL COMMENT ''已发送的提醒记录，JSON格式''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. 提醒设置表
CREATE TABLE IF NOT EXISTS reminder_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled TINYINT DEFAULT 1 COMMENT '是否启用提醒',
    reminder_days VARCHAR(100) DEFAULT '3,1' COMMENT '提前提醒天数，多个用逗号分隔',
    reminder_time VARCHAR(10) DEFAULT '09:00' COMMENT '每日提醒时间',
    overdue_reminder_enabled TINYINT DEFAULT 0 COMMENT '是否启用逾期提醒',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同到期提醒设置';

-- 3. 系统消息表
CREATE TABLE IF NOT EXISTS system_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL COMMENT '消息类型：CONTRACT_EXPIRE-合同到期提醒',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content TEXT NOT NULL COMMENT '消息内容',
    related_id BIGINT COMMENT '关联ID（合同ID）',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息';

-- 4. 初始化默认提醒设置（仅当表为空时插入，避免重复执行产生多行）
INSERT INTO reminder_settings (enabled, reminder_days, reminder_time, overdue_reminder_enabled)
SELECT 1, '3,1', '09:00', 0
WHERE NOT EXISTS (SELECT 1 FROM reminder_settings);
