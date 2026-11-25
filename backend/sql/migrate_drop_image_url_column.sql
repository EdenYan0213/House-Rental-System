-- Migration: 物理删除 room / building 的 image_url 列
--
-- 前置条件（已确认）：
--   1) 两表的 image_url 均无有效数据（已由 migrate_merge_image_url_into_ids.sql 迁移）
--   2) operation_snapshot 的 before_data / after_data 中不再出现 imageUrl 键
--   3) 实体类已移除该字段，代码无引用
--
-- ⚠️ 本操作不可逆。执行前请备份。
--
-- 若你的库仍处于旧版本，请先依次执行：
--   migrate_clean_dangling_image_refs.sql
--   migrate_merge_image_url_into_ids.sql
-- 再执行本脚本。

USE house_db;

-- 安全检查：仍有非空 image_url 时中止（用 SIGNAL 抛错，避免静默丢数据）
DROP PROCEDURE IF EXISTS `sp_assert_image_url_empty`;

DELIMITER $$

CREATE PROCEDURE `sp_assert_image_url_empty`()
BEGIN
  DECLARE room_cnt INT DEFAULT 0;
  DECLARE building_cnt INT DEFAULT 0;

  SELECT COUNT(*) INTO room_cnt FROM room WHERE image_url IS NOT NULL AND image_url <> '';
  SELECT COUNT(*) INTO building_cnt FROM building WHERE image_url IS NOT NULL AND image_url <> '';

  IF room_cnt > 0 OR building_cnt > 0 THEN
    SET @msg = CONCAT('中止：仍有 image_url 数据（room=', room_cnt,
                      ', building=', building_cnt, '），请先执行数据迁移脚本');
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
  END IF;
END$$

DELIMITER ;

CALL sp_assert_image_url_empty();
DROP PROCEDURE IF EXISTS `sp_assert_image_url_empty`;

-- 幂等删列：MySQL 8.0 不支持 DROP COLUMN IF EXISTS，用 information_schema 判断
DROP PROCEDURE IF EXISTS `sp_drop_column_if_exists`;

DELIMITER $$

CREATE PROCEDURE `sp_drop_column_if_exists`(
  IN p_table VARCHAR(64),
  IN p_column VARCHAR(64)
)
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

DELIMITER ;

CALL sp_drop_column_if_exists('room', 'image_url');
CALL sp_drop_column_if_exists('building', 'image_url');

DROP PROCEDURE IF EXISTS `sp_drop_column_if_exists`;

-- 核对：应当无 image_url 列
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'image_url'
  AND TABLE_NAME IN ('room', 'building');
