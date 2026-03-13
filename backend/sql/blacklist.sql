-- 黑名单管理功能 - 数据库结构变更

-- 1. 为tenant表添加黑名单相关字段
ALTER TABLE tenant ADD COLUMN is_blacklisted TINYINT DEFAULT 0 COMMENT '是否在黑名单中';
ALTER TABLE tenant ADD COLUMN blacklist_reason VARCHAR(255) DEFAULT NULL COMMENT '加入黑名单原因';
ALTER TABLE tenant ADD COLUMN blacklisted_at DATETIME DEFAULT NULL COMMENT '加入黑名单时间';
ALTER TABLE tenant ADD COLUMN blacklisted_by BIGINT DEFAULT NULL COMMENT '操作人ID';

-- 2. 添加索引优化查询性能
CREATE INDEX idx_tenant_blacklisted ON tenant(is_blacklisted);
