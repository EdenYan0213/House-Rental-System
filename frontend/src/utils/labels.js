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
