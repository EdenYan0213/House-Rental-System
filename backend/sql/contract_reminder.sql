-- 已废弃：内容已迁移到 migrate_add_contract_reminder.sql
--
-- 原因：本文件名不在 `migrate_*.sql` 通配范围内，部署流程不会执行它，
-- 导致 reminder_settings / system_message 两张表永远不会被创建
-- （表现为 GET /api/reminder-settings、/api/messages 返回 500）。
--
-- 请执行：mysql < backend/sql/migrate_add_contract_reminder.sql
-- 本文件仅为兼容旧文档/旧脚本引用而保留，内容为空。

USE house_db;

SELECT '此脚本已废弃，请执行 migrate_add_contract_reminder.sql' AS notice;
