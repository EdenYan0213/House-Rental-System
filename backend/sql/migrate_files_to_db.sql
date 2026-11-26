-- 统一文件存储到数据库：image_blob 增加 stored_name 列。
--
-- 背景：项目此前有两套存储——/api/images/* 写 image_blob（数据库），
-- /api/files/* 写本地磁盘（app.file.upload-dir）。磁盘方式在容器化部署
-- （无持久卷）下重启即丢文件，且数据库备份不包含这些文件。
-- 现在 /api/files/* 也改为写 image_blob，用 stored_name 保存原磁盘文件名，
-- 以便旧 URL（/api/files/<filename>）继续可访问。
--
-- 幂等：可重复执行。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'image_blob'
    AND COLUMN_NAME = 'stored_name'
);

SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `image_blob` ADD COLUMN `stored_name` varchar(255) NULL COMMENT ''原磁盘文件名，用于兼容旧 /api/files/<name> URL'' AFTER `original_name`',
  'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'image_blob'
    AND INDEX_NAME = 'idx_image_blob_stored_name'
);

SET @sql2 := IF(@idx_exists = 0,
  'ALTER TABLE `image_blob` ADD KEY `idx_image_blob_stored_name` (`stored_name`)',
  'SELECT 1');

PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
