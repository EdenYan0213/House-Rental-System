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
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），改用 information_schema 判断
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'email') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `email` varchar(100) COMMENT ''邮箱''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'enabled') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `enabled` tinyint(1) DEFAULT 1 COMMENT ''是否启用: 1-启用, 0-禁用''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
                  AND COLUMN_NAME = 'last_login_time') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `last_login_time` datetime COMMENT ''最后登录时间''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 把已有 admin 提升为超级管理员
UPDATE `sys_user` SET `role` = 'SUPER_ADMIN', `enabled` = 1 WHERE `username` = 'admin';

-- 不再在此插入 admin / manager / user1：
-- 原先插入的密码哈希对应一个未知口令，会让 ADMIN_INIT_PASSWORD 因「账号已存在」
-- 被跳过，最终把人锁在账号外；额外插入的 manager/user1 更会在生产库里留下未知口令的账号。
-- 管理员由应用首次启动时按 ADMIN_INIT_PASSWORD 播种，其余账号请登录后在「用户管理」里创建。

-- 操作日志表增加用户ID字段
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'operation_log'
                  AND COLUMN_NAME = 'user_id') = 0,
  'ALTER TABLE `operation_log` ADD COLUMN `user_id` bigint COMMENT ''操作用户ID''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
