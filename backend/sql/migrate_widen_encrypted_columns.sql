-- Migration: 加宽敏感字段列，容纳 AES/GCM 密文
--
-- 背景（这是一个真实 bug 的收尾）：
--   CryptoConverter 旧实现把密钥写成 18 字节（"ChangeThisSecret!!"），
--   而 AES 只接受 16/24/32 字节，Cipher.init 必然抛 InvalidKeyException。
--   异常被 catch 后原样返回明文，于是「加密」从未生效：
--   库里 tenant.id_card、sms_config.access_secret 一直是明文，
--   列宽也就一直按明文长度（varchar(18)）设计。
--
--   修好转换器后，密文格式为 v2:<base64 iv>:<base64 ciphertext>，
--   18 位身份证约产生 60 字符，varchar(18) 会报 Data truncation，
--   所以必须先加宽列，再由应用启动时的 SensitiveDataEncryptionMigrator
--   把明文就地加密。
--
-- 幂等：MODIFY COLUMN 本身可重复执行。

USE house_db;

-- 18 位身份证 → 密文约 60 字符；留足余量给未来更长的证件号
ALTER TABLE tenant
  MODIFY COLUMN id_card VARCHAR(255) NULL COMMENT '身份证号（AES/GCM 密文，v2: 前缀）';

-- 阿里云 AccessKey Secret 通常 30~50 字符，密文同样会超过原长度
ALTER TABLE sms_config
  MODIFY COLUMN access_secret VARCHAR(512) NULL COMMENT '短信密钥（AES/GCM 密文）';

-- 核对：列宽应为 255 / 512
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'tenant' AND COLUMN_NAME = 'id_card')
    OR (TABLE_NAME = 'sms_config' AND COLUMN_NAME = 'access_secret'));
