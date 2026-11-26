-- Migration: add building image url column
--
-- 说明：image_url 后来已废弃（改用 image_ids 引用 image_blob），
-- 后续 migrate_drop_image_url_column.sql 会删除它。
-- 这里做幂等处理，避免重复执行报 1060 Duplicate column。

USE house_db;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building'
                  AND COLUMN_NAME = 'image_url') = 0,
  'ALTER TABLE `building` ADD COLUMN `image_url` varchar(500) NULL COMMENT ''楼栋图片URL''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
