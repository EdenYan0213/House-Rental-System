/**
 * 账单相关的纯展示/判定逻辑。
 *
 * <p>从 1077 行的 `Bills.vue` 抽出。这里全部是无副作用函数，
 * 不依赖 Vue 响应式，可单独推演与复用（移动端也有类似判断）。
 */

/** 已支付或已作废的账单不再允许收款/作废。 */
export function isBillFinal(row) {
  const status = String(row?.status || '').trim().toUpperCase()
  return ['PAID', 'VOID', 'OBSOLETE'].includes(status)
}

/** 待收金额：总额减已付，不为负。 */
export function remainOf(bill) {
  const total = Number(bill?.totalAmount || 0)
  const paid = Number(bill?.paidAmount || 0)
  return Math.max(total - paid, 0)
}

/** 金额千分位，不带符号。 */
export function formatMoneyValue(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/**
 * 是否逾期。
 *
 * <p>到期日缺失时无法判断，返回 false 避免误标；
 * 已结清/已作废的账单也不算逾期。
 */
export function isOverdue(row) {
  if (isBillFinal(row) || !row.dueDate) return false
  return new Date(row.dueDate) < new Date(new Date().toDateString())
}

/** 逾期天数，按自然日计算，未逾期为 0。 */
export function overdueDaysOf(row) {
  if (!row?.dueDate) return 0
  const diff = new Date(new Date().toDateString()) - new Date(new Date(row.dueDate).toDateString())
  return Math.max(Math.floor(diff / 86400000), 0)
}

/** 水电读数都为空/0 时视为未抄表，列表上给出提示。 */
export function hasMeterData(row) {
  const water = Number(row?.waterUsage || 0)
  const electric = Number(row?.electricUsage || 0)
  return water > 0 || electric > 0
}
