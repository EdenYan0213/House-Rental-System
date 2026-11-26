-- Migration: 归一化历史数据中的房间/账单状态写法
--
-- 背景：同一状态在历史数据中存在中英文两种写法。
--   room.status  : IDLE / RENTED / MAINTENANCE  <->  空闲 / 已租 / 维修
--   bill.status  : PENDING / PARTIAL / PAID / VOID  <->  待支付 / 部分支付 / 已支付 / 作废
--
-- 代码侧已通过 RoomStatus / BillStatus 归一化兼容两种写法，
-- 本脚本把库内数据统一到规范值（房间状态→英文大写，账单状态→英文大写），以便索引与筛选稳定。
--
-- 注意：请在执行前备份。脚本可重复执行（幂等）。

USE house_db;

-- ---------- 房间状态：统一为英文 ----------
UPDATE `room` SET `status` = 'IDLE'
  WHERE `status` IS NULL OR `status` = '' OR `status` IN ('空闲', '空置', '待租');

UPDATE `room` SET `status` = 'RENTED'
  WHERE `status` IN ('已租', '在租', '已出租');

UPDATE `room` SET `status` = 'MAINTENANCE'
  WHERE `status` IN ('维修', '维修中');

-- 兜底：其余仍为小写英文的写法统一转大写
UPDATE `room` SET `status` = UPPER(`status`)
  WHERE `status` REGEXP '^[a-z_]+$';

-- ---------- 账单状态：统一为英文大写 ----------
UPDATE `bill` SET `status` = 'PENDING'
  WHERE `status` IS NULL OR `status` = '' OR `status` IN ('未支付', '待支付', '待付款');

UPDATE `bill` SET `status` = 'PARTIAL'
  WHERE `status` IN ('部分支付', '部分付款');

UPDATE `bill` SET `status` = 'PAID'
  WHERE `status` IN ('已支付', '已付清');

UPDATE `bill` SET `status` = 'VOID'
  WHERE `status` IN ('作废', '已作废') OR UPPER(`status`) = 'OBSOLETE';

-- 兜底：其余仍为小写英文的写法统一转大写
UPDATE `bill` SET `status` = UPPER(`status`)
  WHERE `status` REGEXP '^[a-z]+$';

-- 建议索引：状态 + 账单月份是账单列表最常用的筛选组合
-- 幂等：索引已存在时跳过，不会报 1061 Duplicate key name。
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bill'
                  AND INDEX_NAME = 'idx_bill_status_month') = 0,
  'ALTER TABLE `bill` ADD INDEX `idx_bill_status_month` (`status`, `bill_month`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room'
                  AND INDEX_NAME = 'idx_room_building_status') = 0,
  'ALTER TABLE `room` ADD INDEX `idx_room_building_status` (`building_id`, `status`)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
