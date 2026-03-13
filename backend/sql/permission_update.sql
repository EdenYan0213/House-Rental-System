-- 权限管理系统数据库更新脚本
-- 执行前请先备份数据库

USE house_db;

-- 检查并创建用户表（如果不存在）
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 修改用户表结构，添加/修改字段
ALTER TABLE `sys_user` 
    MODIFY COLUMN `role` varchar(20) DEFAULT 'USER' COMMENT '角色: SUPER_ADMIN/ADMIN/USER',
    MODIFY COLUMN `password` varchar(255) NOT NULL COMMENT '密码';

-- 添加缺失的字段
ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS `email` varchar(100) COMMENT '邮箱';
ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用: 1-启用, 0-禁用';
ALTER TABLE `sys_user` ADD COLUMN IF NOT EXISTS `last_login_time` datetime COMMENT '最后登录时间';

-- 更新admin用户为超级管理员
UPDATE `sys_user` SET `role` = 'SUPER_ADMIN', `enabled` = 1 WHERE `username` = 'admin';

-- 如果没有admin用户，则创建（密码: admin123）
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`, `enabled`)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', 'SUPER_ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'admin');

-- 创建示例用户（密码都是 123456）
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `role`, `enabled`)
SELECT 'manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '运营经理', '13800138001', 'ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'manager');

INSERT INTO `sys_user` (`username`, `password`, `real_name`, `phone`, `role`, `enabled`)
SELECT 'user1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '前台接待', '13800138002', 'USER', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'user1');

-- 操作日志表增加用户ID字段
ALTER TABLE `operation_log` ADD COLUMN IF NOT EXISTS `user_id` bigint COMMENT '操作用户ID';
