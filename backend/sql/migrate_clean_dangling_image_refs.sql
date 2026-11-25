-- Migration: 清理悬空的图片引用
--
-- 背景：room.image_ids / building.image_ids 里记录的图片 ID 在 image_blob 表中
-- 已不存在（图片迁移或孤儿清理只删了 blob，没有回写业务表引用）。
-- 前端渲染这些 ID 会拿到 404，露出浏览器默认的破损图标。
--
-- 策略：把「全部引用都已失效」的行重置为空；只要还有有效 ID 就保留原值
-- （部分失效的极端情况由前端 @error 兜底过滤，避免这里做复杂的 JSON 手术）。
-- 幂等，可重复执行。

USE house_db;

-- ---------- room ----------
UPDATE room
SET image_ids = '[]', image_url = NULL
WHERE image_ids IS NOT NULL
  AND image_ids <> '[]'
  AND NOT EXISTS (
    SELECT 1 FROM image_blob ib
    WHERE ib.id IN (
      SELECT CAST(jt.v AS UNSIGNED)
      FROM JSON_TABLE(room.image_ids, '$[*]' COLUMNS (v VARCHAR(32) PATH '$')) jt
    )
  );

-- ---------- building ----------
UPDATE building
SET image_ids = '[]', image_url = NULL
WHERE image_ids IS NOT NULL
  AND image_ids <> '[]'
  AND NOT EXISTS (
    SELECT 1 FROM image_blob ib
    WHERE ib.id IN (
      SELECT CAST(jt.v AS UNSIGNED)
      FROM JSON_TABLE(building.image_ids, '$[*]' COLUMNS (v VARCHAR(32) PATH '$')) jt
    )
  );

-- 旧的单图字段（UUID 文件名，非 http 链接）在 blob 被清理后也失去意义
UPDATE room SET image_url = NULL
WHERE image_url IS NOT NULL AND image_url NOT LIKE 'http%' AND image_url NOT LIKE '/api/%';

UPDATE building SET image_url = NULL
WHERE image_url IS NOT NULL AND image_url NOT LIKE 'http%' AND image_url NOT LIKE '/api/%';

-- 核对：应当返回 0 行
SELECT 'room' AS tbl, r.id, r.image_ids
FROM room r
WHERE r.image_ids IS NOT NULL AND r.image_ids <> '[]'
  AND NOT EXISTS (
    SELECT 1 FROM image_blob ib
    WHERE ib.id IN (
      SELECT CAST(jt.v AS UNSIGNED)
      FROM JSON_TABLE(r.image_ids, '$[*]' COLUMNS (v VARCHAR(32) PATH '$')) jt
    )
  )
UNION ALL
SELECT 'building', b.id, b.image_ids
FROM building b
WHERE b.image_ids IS NOT NULL AND b.image_ids <> '[]'
  AND NOT EXISTS (
    SELECT 1 FROM image_blob ib
    WHERE ib.id IN (
      SELECT CAST(jt.v AS UNSIGNED)
      FROM JSON_TABLE(b.image_ids, '$[*]' COLUMNS (v VARCHAR(32) PATH '$')) jt
    )
  );
