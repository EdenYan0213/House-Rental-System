-- Migration: add building-level water/electric unit price columns
-- 幂等：重复执行不会因重复列报错。

USE house_db;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building'
                  AND COLUMN_NAME = 'water_unit_price') = 0,
  'ALTER TABLE `building` ADD COLUMN `water_unit_price` decimal(10,4) NULL COMMENT ''楼栋水费单价''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building'
                  AND COLUMN_NAME = 'electric_unit_price') = 0,
  'ALTER TABLE `building` ADD COLUMN `electric_unit_price` decimal(10,4) NULL COMMENT ''楼栋电费单价''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
