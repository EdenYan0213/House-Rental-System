-- Migration: add building image url column

USE house_db;

ALTER TABLE `building`
  ADD COLUMN `image_url` varchar(500) NULL COMMENT '楼栋图片URL';
