-- Migration: 补齐实体已声明但 init_db.sql 遗漏的列
--
-- 背景：JPA 实体（Room/Tenant/Contract/Bill）声明了若干字段，
-- 但 init_db.sql 的建表语句没有对应列，且此前没有对应的迁移脚本。
-- 结果是全新环境执行 init_db.sql 后，任何涉及这些实体的查询都会报
-- "Unknown column 'r1_0.deleted'" 之类的错误。
--
-- 本脚本幂等：通过 information_schema 判断列/索引是否存在后再添加，
-- 可重复执行（MySQL 不支持 ADD COLUMN IF NOT EXISTS）。

USE house_db;

DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
DROP PROCEDURE IF EXISTS `sp_add_index_if_missing`;

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

CREATE PROCEDURE `sp_add_index_if_missing`(
  IN p_table VARCHAR(64),
  IN p_index VARCHAR(64),
  IN p_columns TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

-- ---------- room ----------
CALL sp_add_column_if_missing('room', 'image_url', 'varchar(500) NULL COMMENT ''房间图片URL''');
CALL sp_add_column_if_missing('room', 'deleted', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''');

-- ---------- tenant ----------
CALL sp_add_column_if_missing('tenant', 'deleted', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''');
CALL sp_add_column_if_missing('tenant', 'is_blacklisted', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否在黑名单''');
CALL sp_add_column_if_missing('tenant', 'blacklist_reason', 'varchar(255) NULL COMMENT ''加入黑名单原因''');
CALL sp_add_column_if_missing('tenant', 'blacklisted_at', 'datetime NULL COMMENT ''加入黑名单时间''');
CALL sp_add_column_if_missing('tenant', 'blacklisted_by', 'bigint NULL COMMENT ''操作人ID''');
CALL sp_add_index_if_missing('tenant', 'idx_tenant_blacklisted', '`is_blacklisted`');

-- ---------- contract ----------
CALL sp_add_column_if_missing('contract', 'deleted', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''');
CALL sp_add_column_if_missing('contract', 'reminder_sent', 'varchar(10) NOT NULL DEFAULT ''0'' COMMENT ''到期提醒标记''');

-- ---------- contract 冗余展示字段 ----------
CALL sp_add_column_if_missing('contract', 'room_number', 'varchar(50) NULL COMMENT ''房间号(冗余)''');
CALL sp_add_column_if_missing('contract', 'building_name', 'varchar(100) NULL COMMENT ''楼栋名(冗余)''');
CALL sp_add_column_if_missing('contract', 'tenant_name', 'varchar(50) NULL COMMENT ''租户姓名(冗余)''');
CALL sp_add_column_if_missing('contract', 'tenant_phone', 'varchar(20) NULL COMMENT ''租户电话(冗余)''');

-- ---------- bill ----------
-- 账单上冗余存房间/租户/楼栋名称，避免列表页逐条关联查询
CALL sp_add_column_if_missing('bill', 'room_id', 'bigint NULL COMMENT ''房间ID''');
CALL sp_add_column_if_missing('bill', 'room_number', 'varchar(50) NULL COMMENT ''房间号''');
CALL sp_add_column_if_missing('bill', 'building_name', 'varchar(100) NULL COMMENT ''楼栋名称''');
CALL sp_add_column_if_missing('bill', 'tenant_name', 'varchar(50) NULL COMMENT ''租户姓名''');
CALL sp_add_column_if_missing('bill', 'tenant_phone', 'varchar(20) NULL COMMENT ''租户手机号''');
CALL sp_add_column_if_missing('bill', 'deleted', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT ''软删除标记''');
CALL sp_add_index_if_missing('bill', 'idx_bill_room_id', '`room_id`');

-- ---------- bill_detail ----------
-- 实体使用 name/usage_amount/prev_reading/curr_reading；
-- init_db.sql 里只有 usage 列，导致账单生成插入失败。
CALL sp_add_column_if_missing('bill_detail', 'name', 'varchar(100) NULL COMMENT ''费用项名称''');
CALL sp_add_column_if_missing('bill_detail', 'usage_amount', 'decimal(10,2) NULL COMMENT ''用量''');
CALL sp_add_column_if_missing('bill_detail', 'prev_reading', 'decimal(12,2) NULL COMMENT ''上期读数''');
CALL sp_add_column_if_missing('bill_detail', 'curr_reading', 'decimal(12,2) NULL COMMENT ''本期读数''');

-- 水电等自动计算项没有对应费用项配置，fee_item_id 必须允许为空
ALTER TABLE `bill_detail` MODIFY COLUMN `fee_item_id` bigint NULL COMMENT '费用项ID(自动计算项可为空)';

DROP PROCEDURE IF EXISTS `sp_add_column_if_missing`;
DROP PROCEDURE IF EXISTS `sp_add_index_if_missing`;
