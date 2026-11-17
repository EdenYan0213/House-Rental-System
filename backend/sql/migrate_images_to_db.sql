-- Migration: 图片存入数据库
--
-- 背景：原先图片落盘到 backend/src/main/resources/imgs/，存在三个问题：
--   1. 上传即落盘，用户取消编辑后文件成为永久孤儿
--   2. 打包成 jar 后该目录不可写
--   3. 多实例部署无法共享
--
-- 现在改为存入 MySQL：image_blob 表保存二进制内容，
-- 楼栋/房间只引用 ID 列表（image_ids），彻底避免磁盘依赖与孤儿文件。

USE house_db;

CREATE TABLE IF NOT EXISTS `image_blob` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `content_type` varchar(100) NOT NULL COMMENT 'MIME类型',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '字节数',
  `original_name` varchar(255) NULL COMMENT '原始文件名',
  `data` longblob NOT NULL COMMENT '图片二进制内容',
  `ref_count` int NOT NULL DEFAULT 0 COMMENT '被引用次数，0 视为孤儿可清理',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_image_blob_created` (`created_at`),
  KEY `idx_image_blob_ref` (`ref_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='图片二进制存储';

-- 楼栋/房间改为存图片ID列表（JSON 数组字符串，如 "[1,2,3]"）
DROP PROCEDURE IF EXISTS `sp_add_image_ids`;
DELIMITER $$

CREATE PROCEDURE `sp_add_image_ids`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building' AND COLUMN_NAME = 'image_ids'
  ) THEN
    ALTER TABLE `building`
      ADD COLUMN `image_ids` text NULL COMMENT '图片ID列表(JSON数组)';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room' AND COLUMN_NAME = 'image_ids'
  ) THEN
    ALTER TABLE `room`
      ADD COLUMN `image_ids` text NULL COMMENT '图片ID列表(JSON数组)';
  END IF;
END$$

DELIMITER ;

CALL sp_add_image_ids();
DROP PROCEDURE IF EXISTS `sp_add_image_ids`;
