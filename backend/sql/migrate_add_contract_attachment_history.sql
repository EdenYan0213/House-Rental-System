-- Migration: 合同附件版本历史
--
-- 背景：contract 只有一个 attachment_url 字段，附件被替换或续租时
-- 旧文件引用会丢失，无法追溯「当时签的是哪一版」。
-- 本表为每次附件变更留存一条记录。
--
-- 本脚本幂等。

USE house_db;

CREATE TABLE IF NOT EXISTS `contract_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '附件记录ID',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本号，从1递增',
  `file_url` varchar(500) NOT NULL COMMENT '文件地址',
  `file_name` varchar(255) NULL COMMENT '原始文件名',
  `file_size` bigint NULL COMMENT '字节数',
  `category` varchar(30) NULL COMMENT '类别: CONTRACT-合同正文/ID_CARD-证件/OTHER-其他',
  `remark` varchar(500) NULL COMMENT '备注',
  `operator` varchar(50) NULL COMMENT '上传人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_contract_attachment_contract` (`contract_id`, `version`),
  CONSTRAINT `fk_contract_attachment_contract`
    FOREIGN KEY (`contract_id`) REFERENCES `contract` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同附件版本历史';

-- 把已有的单字段附件回填成 v1 记录（避免历史数据在界面上「凭空消失」）
INSERT INTO `contract_attachment` (`contract_id`, `version`, `file_url`, `category`, `remark`, `created_at`)
SELECT c.id, 1, c.attachment_url, 'CONTRACT', '历史附件（自动迁移）', COALESCE(c.created_at, NOW())
FROM `contract` c
WHERE c.attachment_url IS NOT NULL
  AND c.attachment_url <> ''
  AND NOT EXISTS (
    SELECT 1 FROM `contract_attachment` a WHERE a.contract_id = c.id
  );
