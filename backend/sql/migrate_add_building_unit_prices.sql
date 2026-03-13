-- Migration: add building-level water/electric unit price columns

USE house_db;

ALTER TABLE `building`
  ADD COLUMN `water_unit_price` decimal(10,4) NULL COMMENT '楼栋水费单价',
  ADD COLUMN `electric_unit_price` decimal(10,4) NULL COMMENT '楼栋电费单价';

-- If you prefer default values, you can use e.g.: ADD COLUMN `water_unit_price` decimal(10,4) NOT NULL DEFAULT 0.00
