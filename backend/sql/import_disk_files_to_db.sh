#!/usr/bin/env bash
# 把 upload-dir 下已有的磁盘文件导入 image_blob，使其在「文件统一存数据库」后仍可访问。
#
# 用法：
#   ./import_disk_files_to_db.sh [upload-dir]
#
# 默认目录取 UPLOAD_DIR 或 ./src/main/resources/imgs（与 application.yml 一致）。
#
# 特性：
#   - 幂等：stored_name 已存在则跳过，可重复执行
#   - 只处理 image_blob 允许的图片类型；其他文件提示跳过
#   - ref_count 设为 1（历史文件已被业务数据引用，不应被孤儿清理任务删除）

set -euo pipefail

UPLOAD_DIR="${1:-${UPLOAD_DIR:-./src/main/resources/imgs}}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-house_db}"

if [ ! -d "$UPLOAD_DIR" ]; then
  echo "目录不存在，无需导入: $UPLOAD_DIR"
  exit 0
fi

count=0
skipped=0

for f in "$UPLOAD_DIR"/*; do
  [ -f "$f" ] || continue

  name="$(basename "$f")"
  ext="${name##*.}"
  ext_lower="$(echo "$ext" | tr '[:upper:]' '[:lower:]')"

  case "$ext_lower" in
    jpg|jpeg) mime="image/jpeg" ;;
    png)      mime="image/png" ;;
    gif)      mime="image/gif" ;;
    webp)     mime="image/webp" ;;
    *)
      echo "跳过（非图片类型）: $name"
      skipped=$((skipped + 1))
      continue
      ;;
  esac

  # 已导入过则跳过
  exists=$(mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -s "$MYSQL_DB" \
    -e "SELECT COUNT(*) FROM image_blob WHERE stored_name = '$name';" 2>/dev/null || echo 0)
  if [ "$exists" != "0" ]; then
    echo "已存在，跳过: $name"
    skipped=$((skipped + 1))
    continue
  fi

  size=$(wc -c < "$f" | tr -d ' ')
  hex=$(xxd -p "$f" | tr -d '\n')

  mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" 2>/dev/null <<SQL
INSERT INTO image_blob (content_type, file_size, original_name, stored_name, data, ref_count, created_at)
VALUES ('$mime', $size, '$name', '$name', UNHEX('$hex'), 1, NOW());
SQL

  echo "已导入: $name (${size} 字节)"
  count=$((count + 1))
done

echo "----"
echo "导入 $count 个，跳过 $skipped 个。"
