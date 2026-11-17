-- Migration: 账单增加应缴截止日 due_date
--
-- 背景：前端账单列表/逾期判断使用 dueDate，但 Bill 实体与数据库都没有这个字段，
-- 导致「逾期天数」永远不显示，催收提醒也失去判断依据。
--
-- 本脚本幂等：列已存在时跳过。

USE house_db;

DROP PROCEDURE IF EXISTS `sp_add_bill_due_date`;
DELIMITER $$

CREATE PROCEDURE `sp_add_bill_due_date`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bill' AND COLUMN_NAME = 'due_date'
  ) THEN
    ALTER TABLE `bill`
      ADD COLUMN `due_date` date NULL COMMENT '应缴截止日' AFTER `bill_month`;
  END IF;
END$$

DELIMITER ;

CALL sp_add_bill_due_date();
DROP PROCEDURE IF EXISTS `sp_add_bill_due_date`;

-- 回填历史数据：应缴截止日 = 账单月份 1 号 + 5 天宽限
UPDATE `bill`
SET `due_date` = DATE_ADD(CONCAT(`bill_month`, '-01'), INTERVAL 5 DAY)
WHERE `due_date` IS NULL AND `bill_month` IS NOT NULL;

DROP PROCEDURE IF EXISTS `sp_add_bill_due_date_idx`;
DELIMITER $$

CREATE PROCEDURE `sp_add_bill_due_date_idx`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bill' AND INDEX_NAME = 'idx_bill_due_date'
  ) THEN
    ALTER TABLE `bill` ADD INDEX `idx_bill_due_date` (`due_date`);
  END IF;
END$$

DELIMITER ;

CALL sp_add_bill_due_date_idx();
DROP PROCEDURE IF EXISTS `sp_add_bill_due_date_idx`;
