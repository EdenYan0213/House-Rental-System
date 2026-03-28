-- Performance index migration (idempotent)

USE house_db;
SET @db = DATABASE();

-- bill: status + month query path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'bill' AND index_name = 'idx_bill_status_month_created'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_bill_status_month_created` ON `bill`(`status`,`bill_month`,`created_at`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- contract: tenant + status path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'contract' AND index_name = 'idx_contract_tenant_status'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_contract_tenant_status` ON `contract`(`tenant_id`,`status`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- contract: expiry reminder path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'contract' AND index_name = 'idx_contract_status_end_date'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_contract_status_end_date` ON `contract`(`status`,`end_date`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- meter_reading: date range query path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'meter_reading' AND index_name = 'idx_meter_record_date_room'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_meter_record_date_room` ON `meter_reading`(`record_date`,`room_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- room: building + status + floor query path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'room' AND index_name = 'idx_room_building_status_floor'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_room_building_status_floor` ON `room`(`building_id`,`status`,`floor`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- operation_log: weekly inspect path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'operation_log' AND index_name = 'idx_operation_time_module'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_operation_time_module` ON `operation_log`(`operation_time`,`module`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- payment_record: payment timeline path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'payment_record' AND index_name = 'idx_payment_paid_date_bill'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_payment_paid_date_bill` ON `payment_record`(`paid_date`,`bill_id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- repair_order: tenant + status path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'repair_order' AND index_name = 'idx_repair_tenant_status_created'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_repair_tenant_status_created` ON `repair_order`(`tenant_id`,`status`,`created_at`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- complaint_suggestion: tenant + status path
SET @exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'complaint_suggestion' AND index_name = 'idx_complaint_tenant_status_created'
);
SET @sql = IF(@exists = 0,
  'CREATE INDEX `idx_complaint_tenant_status_created` ON `complaint_suggestion`(`tenant_id`,`status`,`created_at`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
