-- Migration: 物理删除 room / building 的 image_urls（复数）列
--
-- 背景：image_urls 是「磁盘文件存储时代」的多图字段，存的是 UUID 文件名数组。
-- 图片迁到 image_blob 后该字段完全失效：
--   - 后端代码零引用
--   - 指向的文件早已不存在
--   - 当前真相是 image_ids
--
-- 幂等，可重复执行。执行前请备份。

USE house_db;

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

CALL sp_drop_column_if_exists('room', 'image_urls');
CALL sp_drop_column_if_exists('building', 'image_urls');

DROP PROCEDURE IF EXISTS `sp_drop_column_if_exists`;

-- 核对：应当无 image_urls 列
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME IN ('image_url', 'image_urls')
  AND TABLE_NAME IN ('room', 'building');
