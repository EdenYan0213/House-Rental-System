-- Create remind_rule, sms_config, remind_log
CREATE TABLE IF NOT EXISTS remind_rule (
  id INT AUTO_INCREMENT PRIMARY KEY,
  overdue_days INT NOT NULL,
  send_time VARCHAR(5) NOT NULL DEFAULT '09:00',
  sms_template TEXT,
  is_enabled TINYINT(1) DEFAULT 0,
  sort_order INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sms_config (
  id INT AUTO_INCREMENT PRIMARY KEY,
  provider VARCHAR(50) DEFAULT 'aliyun',
  access_key VARCHAR(255),
  access_secret VARCHAR(255),
  signature VARCHAR(50),
  template_code VARCHAR(100),
  debug_mode TINYINT(1) DEFAULT 1,
  status TINYINT(1) DEFAULT 1
);

CREATE TABLE IF NOT EXISTS remind_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id INT,
  tenant_name VARCHAR(50),
  phone VARCHAR(20),
  room_number VARCHAR(50),
  overdue_days INT,
  bill_ids JSON,
  total_amount DECIMAL(10,2),
  content TEXT,
  status ENUM('success','failed') DEFAULT 'success',
  fail_reason TEXT,
  is_debug TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for quick lookup
CREATE INDEX idx_remind_log_tenant_overdue ON remind_log(tenant_id, overdue_days);