-- Migration: 把遗留的 image_url 数据合并到 image_ids，随后清空 image_url
--
-- 背景：图片存储从「单字段 URL」迁移到「image_blob + image_ids」后，
-- image_url 字段成为历史包袱。它可能是：
--   1) /api/images/{id}   —— 新格式，可解析出 ID 合并进 image_ids
--   2) 纯文件名（UUID.jpg）—— 旧磁盘存储，blob 里已不存在，只能丢弃
--   3) http(s) 外链        —— 外部图片，保留（image_ids 表达不了外部链接）
--
-- 本脚本只处理 1) 并把 image_ids 里缺失的 ID 补进去；2) 直接清空（图片已丢）；
-- 3) 原样保留。幂等，可重复执行。

USE house_db;

-- 若 image_url 列已不存在（新库由 init_db.sql 直接建为 image_ids 结构），
-- 本脚本无历史数据可迁移，全部跳过。用存储过程包住以便条件执行。
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'building'
                   AND COLUMN_NAME = 'image_url');

-- 把原有 UPDATE 语句包进 IF 分支：列存在才执行
DROP PROCEDURE IF EXISTS tmp_merge_image_url;
DELIMITER $$
CREATE PROCEDURE tmp_merge_image_url()
BEGIN
  IF @has_col > 0 THEN
    -- ---------- 1) /api/images/{id} → image_ids ----------
    UPDATE building
    SET image_ids = CASE
          WHEN image_ids IS NULL OR image_ids = '[]' OR image_ids = '' THEN
            CONCAT('[', CAST(SUBSTRING_INDEX(image_url, '/', -1) AS UNSIGNED), ']')
          WHEN JSON_CONTAINS(image_ids, CAST(SUBSTRING_INDEX(image_url, '/', -1) AS JSON)) THEN
            image_ids
          ELSE
            JSON_ARRAY_APPEND(
              image_ids, '$', CAST(SUBSTRING_INDEX(image_url, '/', -1) AS UNSIGNED))
        END
    WHERE image_url LIKE '/api/images/%'
      AND SUBSTRING_INDEX(image_url, '/', -1) REGEXP '^[0-9]+$'
      AND EXISTS (
        SELECT 1 FROM image_blob ib
        WHERE ib.id = CAST(SUBSTRING_INDEX(building.image_url, '/', -1) AS UNSIGNED)
      );

    UPDATE room
    SET image_ids = CASE
          WHEN image_ids IS NULL OR image_ids = '[]' OR image_ids = '' THEN
            CONCAT('[', CAST(SUBSTRING_INDEX(image_url, '/', -1) AS UNSIGNED), ']')
          WHEN JSON_CONTAINS(image_ids, CAST(SUBSTRING_INDEX(image_url, '/', -1) AS JSON)) THEN
            image_ids
          ELSE
            JSON_ARRAY_APPEND(
              image_ids, '$', CAST(SUBSTRING_INDEX(image_url, '/', -1) AS UNSIGNED))
        END
    WHERE image_url LIKE '/api/images/%'
      AND SUBSTRING_INDEX(image_url, '/', -1) REGEXP '^[0-9]+$'
      AND EXISTS (
        SELECT 1 FROM image_blob ib
        WHERE ib.id = CAST(SUBSTRING_INDEX(room.image_url, '/', -1) AS UNSIGNED)
      );

    -- ---------- 2) 清空已失效的本地文件名 ----------
    -- 纯文件名（不含 /）说明是旧磁盘存储的残留，blob 里没有对应数据
    UPDATE building SET image_url = NULL
    WHERE image_url IS NOT NULL
      AND image_url NOT LIKE 'http%'
      AND image_url NOT LIKE '/%';

    UPDATE room SET image_url = NULL
    WHERE image_url IS NOT NULL
      AND image_url NOT LIKE 'http%'
      AND image_url NOT LIKE '/%';

    -- ---------- 3) 已并入 image_ids 的 /api/images/* 也清空，避免双份真相 ----------
    UPDATE building SET image_url = NULL WHERE image_url LIKE '/api/images/%';
    UPDATE room SET image_url = NULL WHERE image_url LIKE '/api/images/%';

    -- 核对：剩余 image_url 应当只有 http(s) 外链
    SELECT 'building' AS tbl, id, image_url FROM building WHERE image_url IS NOT NULL
    UNION ALL
    SELECT 'room', id, image_url FROM room WHERE image_url IS NOT NULL;
  END IF;
END$$
DELIMITER ;

CALL tmp_merge_image_url();
DROP PROCEDURE tmp_merge_image_url;
