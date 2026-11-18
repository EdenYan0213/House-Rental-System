/**
 * 移动端通用格式化。
 *
 * 后端返回的时间多为 ISO 字符串（如 2026-11-18T02:35:15），
 * 直接渲染很长且不友好，统一在这里转成「YYYY-MM-DD HH:mm」。
 */

/** ISO / Date → 'YYYY-MM-DD HH:mm'；无效值返回占位符。 */
export function formatDateTime(value, fallback = '-') {
  if (!value) return fallback
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) {
    // 无法解析时退化为去掉 T 的原始字符串
    return String(value).replace('T', ' ').slice(0, 16)
  }
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 只取日期部分 'YYYY-MM-DD'。 */
export function formatDate(value, fallback = '-') {
  if (!value) return fallback
  return formatDateTime(value, fallback).slice(0, 10)
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前，超过 7 天显示日期。 */
export function formatRelative(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const diffMs = Date.now() - d.getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days <= 7) return `${days} 天前`
  return formatDate(value)
}

/** 金额格式化：¥1,234.00 */
export function formatMoney(value) {
  return `￥${Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}
