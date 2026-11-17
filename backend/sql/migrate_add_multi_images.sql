-- Migration: 楼栋/房间支持多图
--
-- 设计说明：
--   image_url  : 保留为「封面图」，前端卡片/列表的默认展示与旧数据兼容
--   image_urls : 新增，JSON 数组，存全部图片；第一张通常等于封面
--
-- 本脚本幂等：列已存在时跳过。

USE house_db;

DROP PROCEDURE IF EXISTS `sp_add_multi_images`;
DELIMITER $$

CREATE PROCEDURE `sp_add_multi_images`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building' AND COLUMN_NAME = 'image_urls'
  ) THEN
    ALTER TABLE `building`
      ADD COLUMN `image_urls` text NULL COMMENT '楼栋图片列表(JSON数组字符串)' AFTER `image_url`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room' AND COLUMN_NAME = 'image_urls'
  ) THEN
    ALTER TABLE `room`
      ADD COLUMN `image_urls` text NULL COMMENT '房间图片列表(JSON数组字符串)' AFTER `image_url`;
  END IF;
END$$

DELIMITER ;

CALL sp_add_multi_images();
DROP PROCEDURE IF EXISTS `sp_add_multi_images`;

-- 回填：把已有的封面图写入 image_urls 数组
UPDATE `building`
SET `image_urls` = CONCAT('["', `image_url`, '"]')
WHERE `image_urls` IS NULL AND `image_url` IS NOT NULL AND `image_url` <> '';

UPDATE `room`
SET `image_urls` = CONCAT('["', `image_url`, '"]')
WHERE `image_urls` IS NULL AND `image_url` IS NOT NULL AND `image_url` <> '';
