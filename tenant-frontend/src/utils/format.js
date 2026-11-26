/**
 * 租客端通用格式化与状态文案。
 *
 * <p>四个页面各自复制过一份 statusText/statusClass/formatTime，口径还不一致
 * （有的按大写判断、有的按小写，报修和投诉的"处理中"集合也不同）。
 * 统一放这里，页面只负责渲染。
 */

/** 账单状态 → 文案。后端可能返回英文或中文，统一归一。 */
export function billStatusText(raw) {
  const value = String(raw || '').toUpperCase()
  if (value === 'PENDING' || value === '待支付' || value === '未支付') return '待支付'
  if (value === 'PARTIAL' || value === '部分支付') return '部分支付'
  if (value === 'PAID' || value === '已支付' || value === '已付清') return '已支付'
  if (value === 'VOID' || value === '作废' || value === '已作废') return '已作废'
  return raw || '-'
}

/** 账单状态 → CSS class（复用全局 tag-* 样式）。 */
export function billStatusClass(raw) {
  const value = String(raw || '').toUpperCase()
  if (value === 'PENDING' || value === '待支付' || value === '未支付') return 'tag tag-pending'
  if (value === 'PAID' || value === '已支付' || value === '已付清') return 'tag tag-paid'
  if (value === 'PARTIAL' || value === '部分支付') return 'tag tag-partial'
  return 'tag tag-void'
}

/** 报修状态集合。与后端 WorkflowStatus 对应，租客视角只分三档。 */
const REPAIR_DOING = ['pending', 'assigned', 'processing', 'pending_accept']
const COMPLAINT_DOING = ['pending', 'assigned', 'processing', 'pending_confirm']

export function repairStatusText(raw) {
  const key = String(raw || '').toLowerCase()
  if (REPAIR_DOING.includes(key)) return '处理中'
  if (key === 'completed') return '已完成'
  if (key === 'cancelled') return '已取消'
  return raw || '-'
}

export function repairStatusClass(raw) {
  const key = String(raw || '').toLowerCase()
  if (REPAIR_DOING.includes(key)) return 'tag tag-pending'
  if (key === 'completed') return 'tag tag-paid'
  return 'tag tag-void'
}

export function complaintStatusText(raw) {
  const key = String(raw || '').toLowerCase()
  if (COMPLAINT_DOING.includes(key)) return '处理中'
  if (key === 'completed' || key === 'closed') return '已完成'
  if (key === 'cancelled') return '已取消'
  return raw || '-'
}

export function complaintStatusClass(raw) {
  const key = String(raw || '').toLowerCase()
  if (COMPLAINT_DOING.includes(key)) return 'tag tag-pending'
  if (key === 'completed' || key === 'closed') return 'tag tag-paid'
  return 'tag tag-void'
}

/** 合同状态 → 文案。 */
export function contractStatusText(raw) {
  const key = String(raw || '').toUpperCase()
  if (key === 'ACTIVE' || raw === '进行中') return '生效中'
  if (key === 'PENDING' || raw === '待生效') return '待生效'
  if (key === 'TERMINATED' || raw === '已终止') return '已终止'
  if (key === 'EXPIRED' || raw === '已到期') return '已到期'
  return raw || '-'
}

/** 续租申请状态 → 文案。后端 RenewApplication.status 是 Integer（0/1/2）。 */
export function renewStatusText(raw) {
  const value = Number(raw)
  if (value === 0) return '待处理'
  if (value === 1) return '已通过'
  if (value === 2) return '已驳回'
  return raw ?? '-'
}

export function renewStatusClass(raw) {
  const value = Number(raw)
  if (value === 0) return 'tag tag-pending'
  if (value === 1) return 'tag tag-paid'
  return 'tag tag-void'
}

/** 时间格式化；后端可能给 ISO 字符串或 null。 */
export function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

/** 只要日期（合同起止等）。 */
export function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleDateString('zh-CN')
}

/** 金额统一两位小数；null/NaN 归零。 */
export function money(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(2) : '0.00'
}
