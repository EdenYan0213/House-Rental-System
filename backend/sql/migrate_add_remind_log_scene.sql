-- Migration: remind_log 增加 scene 字段，区分催缴短信与验证码短信
--
-- 背景：租户登录、修改手机号的验证码短信复用了 remind_log 表，
-- room_number 被写入「租客登录」「手机号修改」这类非房间值，
-- 导致管理端「催缴记录」页混入大量验证码短信，无法区分也无从筛选。
--
-- 本脚本幂等，可重复执行。

USE house_db;

DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;

DELIMITER $$

CREATE PROCEDURE `sp_add_column_if_missing`(
  IN p_table VARCHAR(64),
  IN p_column VARCHAR(64),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

CALL sp_add_column_if_missing(
  'remind_log',
  'scene',
  'varchar(32) NOT NULL DEFAULT ''REMIND'' COMMENT ''短信场景：REMIND 催缴 / LOGIN_CODE 登录验证码 / UPDATE_PHONE 改手机号验证码 / TEST 测试'''
);

DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;

-- 历史数据回填：room_number 为「租客登录」「手机号修改」的即为验证码短信
UPDATE remind_log
SET scene = 'LOGIN_CODE'
WHERE scene = 'REMIND' AND room_number = '租客登录';

UPDATE remind_log
SET scene = 'UPDATE_PHONE'
WHERE scene = 'REMIND' AND room_number = '手机号修改';
