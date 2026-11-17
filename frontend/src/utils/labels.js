/**
 * 枚举值 → 中文展示名。
 *
 * 约定：代码与数据库一律存英文（大写），只有展示层做中文翻译。
 * 页面**不要**自己维护映射表，也**不要**直接把英文原值渲染到界面上。
 */

// ---------------- 房间状态 ----------------
export const ROOM_STATUS = {
  IDLE: '空闲',
  RENTED: '已租',
  MAINTENANCE: '维修中'
}

export const ROOM_STATUS_TAG = {
  IDLE: 'success',
  RENTED: 'danger',
  MAINTENANCE: 'warning'
}

// ---------------- 账单状态 ----------------
export const BILL_STATUS = {
  PENDING: '待付款',
  PARTIAL: '部分付款',
  PAID: '已付清',
  VOID: '已作废'
}

export const BILL_STATUS_TAG = {
  PENDING: 'danger',
  PARTIAL: 'warning',
  PAID: 'success',
  VOID: 'info'
}

// ---------------- 合同状态 ----------------
export const CONTRACT_STATUS = {
  PENDING: '待生效',
  ACTIVE: '进行中',
  EXPIRED: '已到期',
  TERMINATED: '已解约'
}

export const CONTRACT_STATUS_TAG = {
  PENDING: 'warning',
  ACTIVE: 'success',
  EXPIRED: 'info',
  TERMINATED: 'danger'
}

// ---------------- 维修工单状态 ----------------
export const REPAIR_STATUS = {
  pending: '待分配',
  assigned: '已分配',
  processing: '维修中',
  pending_accept: '待验收',
  completed: '已完成',
  cancelled: '已取消'
}

export const REPAIR_STATUS_TAG = {
  pending: 'info',
  assigned: 'info',
  processing: 'warning',
  pending_accept: 'primary',
  completed: 'success',
  cancelled: 'danger'
}

// ---------------- 付款方式（合同周期） ----------------
export const PAYMENT_CYCLE = {
  MONTHLY: '月付',
  QUARTERLY: '季付',
  HALF_YEAR: '半年付',
  YEARLY: '年付'
}

// ---------------- 收款方式 ----------------
export const PAYMENT_METHOD = {
  CASH: '现金',
  ALIPAY: '支付宝',
  WECHAT: '微信',
  BANK: '银行转账'
}

// ---------------- 器具状态 ----------------
export const APPLIANCE_STATUS = {
  NORMAL: '正常',
  DAMAGED: '损坏',
  LOST: '丢失',
  正常: '正常',
  损坏: '损坏',
  丢失: '丢失'
}

// ---------------- 投诉状态 ----------------
export const COMPLAINT_STATUS = {
  pending: '待受理',
  processing: '处理中',
  pending_confirm: '待确认',
  completed: '已完成',
  closed: '已关闭'
}

export const COMPLAINT_STATUS_TAG = {
  pending: 'info',
  processing: 'warning',
  pending_confirm: 'primary',
  completed: 'success',
  closed: 'danger'
}

// ---------------- 短信发送状态 ----------------
export const SMS_STATUS = {
  success: '发送成功',
  failed: '发送失败',
  pending: '待发送',
  skipped: '已跳过'
}

export const SMS_STATUS_TAG = {
  success: 'success',
  failed: 'danger',
  pending: 'info',
  skipped: 'info'
}

// ---------------- 备份类型 / 状态 ----------------
export const BACKUP_TYPE = {
  auto: '自动',
  manual: '手动'
}

export const BACKUP_STATUS = {
  success: '成功',
  failed: '失败'
}

// ---------------- 押金类型 ----------------
export const DEPOSIT_TYPE = {
  RECEIVE: '收取',
  REFUND: '退还'
}

// ---------------- 投诉紧急程度 ----------------
export const COMPLAINT_PRIORITY = {
  normal: '一般',
  urgent: '紧急'
}

// ---------------- 预警规则类型 ----------------
export const ALERT_RULE_TYPE = {
  high_frequency: '短时高频操作',
  login_failures: '连续登录失败',
  sensitive_operation: '敏感操作'
}

// ---------------- 操作动作 ----------------
export const LOG_ACTION = {
  login: '登录',
  logout: '登出',
  add: '新增',
  create: '新增',
  edit: '编辑',
  update: '编辑',
  delete: '删除',
  remove: '删除',
  void: '作废',
  terminate: '解约',
  move_out: '退租',
  renew: '续租',
  pay: '收款',
  export: '导出',
  import: '导入',
  custom: '其他操作'
}

// ---------------- 用户角色 ----------------
export const USER_ROLE = {
  SUPER_ADMIN: '超级管理员',
  ADMIN: '管理员',
  USER: '普通用户'
}

/**
 * 统一的查表函数：命中返回中文，未命中返回兜底文案（默认原值，避免出现空白）。
 * 用于所有 status / type 类字段的展示。
 */
export function label(map, value, fallback) {
  if (value === null || value === undefined || value === '') {
    return fallback ?? '-'
  }
  if (Object.prototype.hasOwnProperty.call(map, value)) {
    return map[value]
  }
  // 兼容大小写不一致的历史数据，如 "pending" / "PENDING"
  const upper = String(value).toUpperCase()
  if (Object.prototype.hasOwnProperty.call(map, upper)) {
    return map[upper]
  }
  const lower = String(value).toLowerCase()
  if (Object.prototype.hasOwnProperty.call(map, lower)) {
    return map[lower]
  }
  return fallback ?? String(value)
}

export const roomStatusText = (v) => label(ROOM_STATUS, v)
export const roomStatusTag = (v) => label(ROOM_STATUS_TAG, v, 'info')
export const billStatusText = (v) => label(BILL_STATUS, v)
export const billStatusTag = (v) => label(BILL_STATUS_TAG, v, 'info')
export const contractStatusText = (v) => label(CONTRACT_STATUS, v)
export const contractStatusTag = (v) => label(CONTRACT_STATUS_TAG, v, 'info')
export const repairStatusText = (v) => label(REPAIR_STATUS, v)
export const repairStatusTag = (v) => label(REPAIR_STATUS_TAG, v, 'info')
export const paymentCycleText = (v) => label(PAYMENT_CYCLE, v)
export const paymentMethodText = (v) => label(PAYMENT_METHOD, v)
export const applianceStatusText = (v) => label(APPLIANCE_STATUS, v)
export const userRoleText = (v) => label(USER_ROLE, v)
export const complaintStatusText = (v) => label(COMPLAINT_STATUS, v)
export const complaintStatusTag = (v) => label(COMPLAINT_STATUS_TAG, v, 'info')
export const smsStatusText = (v) => label(SMS_STATUS, v)
export const smsStatusTag = (v) => label(SMS_STATUS_TAG, v, 'info')
export const backupTypeText = (v) => label(BACKUP_TYPE, v)
export const backupStatusText = (v) => label(BACKUP_STATUS, v)
export const depositTypeText = (v) => label(DEPOSIT_TYPE, v)
export const complaintPriorityText = (v) => label(COMPLAINT_PRIORITY, v)
export const alertRuleTypeText = (v) => label(ALERT_RULE_TYPE, v)
export const logActionText = (v) => label(LOG_ACTION, v)
