ALTER TABLE `sys_user`
ADD COLUMN `theme_preference` varchar(20) NOT NULL DEFAULT 'system' COMMENT '主题偏好: light/dark/system' AFTER `last_login_time`;
