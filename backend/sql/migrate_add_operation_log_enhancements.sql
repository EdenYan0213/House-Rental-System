-- 操作日志增强迁移
set @db = database();

-- operation_log 字段增强
set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='user_id'),
  'select 1',
  'alter table operation_log add column user_id bigint null comment ''操作用户ID'' after id'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='username'),
  'select 1',
  'alter table operation_log add column username varchar(50) null comment ''用户名'' after user_id'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='business_id'),
  'select 1',
  'alter table operation_log add column business_id varchar(100) null comment ''业务对象ID'' after action'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='business_name'),
  'select 1',
  'alter table operation_log add column business_name varchar(255) null comment ''业务对象名称'' after business_id'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='detail_before'),
  'select 1',
  'alter table operation_log add column detail_before json null comment ''操作前快照'' after business_name'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='detail_after'),
  'select 1',
  'alter table operation_log add column detail_after json null comment ''操作后快照'' after detail_before'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='change_summary'),
  'select 1',
  'alter table operation_log add column change_summary text null comment ''变更摘要'' after detail_after'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='request_ip'),
  'select 1',
  'alter table operation_log add column request_ip varchar(50) null comment ''请求IP'' after change_summary'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='user_agent'),
  'select 1',
  'alter table operation_log add column user_agent varchar(255) null comment ''UserAgent'' after request_ip'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='request_params'),
  'select 1',
  'alter table operation_log add column request_params text null comment ''请求参数'' after user_agent'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='result'),
  'select 1',
  'alter table operation_log add column result tinyint not null default 1 comment ''1成功0失败'' after request_params'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='fail_reason'),
  'select 1',
  'alter table operation_log add column fail_reason text null comment ''失败原因'' after result'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='duration_ms'),
  'select 1',
  'alter table operation_log add column duration_ms int null comment ''耗时毫秒'' after fail_reason'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.columns where table_schema=@db and table_name='operation_log' and column_name='log_level'),
  'select 1',
  'alter table operation_log add column log_level tinyint not null default 1 comment ''1-INFO 2-WARN 3-ERROR'' after duration_ms'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 数据回填
update operation_log set username = operator where (username is null or username = '') and operator is not null;
update operation_log set request_ip = ip where (request_ip is null or request_ip = '') and ip is not null;

-- 索引
set @sql = if(
  exists(select 1 from information_schema.statistics where table_schema=@db and table_name='operation_log' and index_name='idx_log_created_module_action'),
  'select 1',
  'create index idx_log_created_module_action on operation_log(created_at, module, action)'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.statistics where table_schema=@db and table_name='operation_log' and index_name='idx_log_business_id'),
  'select 1',
  'create index idx_log_business_id on operation_log(business_id)'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.statistics where table_schema=@db and table_name='operation_log' and index_name='idx_log_user_time'),
  'select 1',
  'create index idx_log_user_time on operation_log(username, operation_time)'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @sql = if(
  exists(select 1 from information_schema.statistics where table_schema=@db and table_name='operation_log' and index_name='idx_log_level_result_duration'),
  'select 1',
  'create index idx_log_level_result_duration on operation_log(log_level, result, duration_ms)'
);
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- 预警规则表
create table if not exists alert_rule (
  id int not null auto_increment,
  rule_type varchar(50) not null,
  title varchar(255) not null,
  enabled tinyint not null default 1,
  threshold_value int null,
  window_minutes int null,
  level tinyint not null default 2,
  notify_channels varchar(255) null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  primary key (id),
  key idx_alert_rule_type (rule_type)
) engine=InnoDB default charset=utf8mb4;

-- 预警记录表
create table if not exists alert_record (
  id int not null auto_increment,
  alert_type varchar(50) not null,
  title varchar(255) not null,
  content text not null,
  level tinyint not null,
  status tinyint not null default 0,
  operator_id int null,
  handled_at datetime null,
  created_at datetime not null default current_timestamp,
  primary key (id),
  key idx_alert_type_status_time (alert_type, status, created_at)
) engine=InnoDB default charset=utf8mb4;

-- 日志归档表
create table if not exists operation_log_archive like operation_log;

-- 默认规则
insert into alert_rule(rule_type, title, enabled, threshold_value, window_minutes, level, notify_channels)
select 'high_frequency', '短时高频操作', 1, 5, 1, 2, 'in_app'
where not exists (select 1 from alert_rule where rule_type='high_frequency');

insert into alert_rule(rule_type, title, enabled, threshold_value, window_minutes, level, notify_channels)
select 'login_failures', '连续登录失败', 1, 3, 5, 3, 'in_app,email'
where not exists (select 1 from alert_rule where rule_type='login_failures');

insert into alert_rule(rule_type, title, enabled, threshold_value, window_minutes, level, notify_channels)
select 'sensitive_operation', '敏感操作提醒', 1, null, 10, 2, 'in_app'
where not exists (select 1 from alert_rule where rule_type='sensitive_operation');
