/**
 * 管理端通用格式化。
 *
 * 后端返回的时间多为 ISO 格式（2026-11-18T02:35:15），直接渲染既长又不友好。
 * 金额、时间、文件大小统一走这里，避免各页面各写一套。
 */

/** ISO / Date → 'YYYY-MM-DD HH:mm'; 无效值返回占位符。 */
export function formatDateTime(value, fallback = '-') {
  if (!value) return fallback
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) {
    return String(value).replace('T', ' ').slice(0, 16)
  }
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** ISO / Date → 'YYYY-MM-DD'。 */
export function formatDate(value, fallback = '-') {
  if (!value) return fallback
  return formatDateTime(value, fallback).slice(0, 10)
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前，超过 7 天回退为日期。 */
export function formatRelative(value, fallback = '-') {
  if (!value) return fallback
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const diff = Date.now() - d.getTime()
  // 未来时间不做相对描述
  if (diff < 0) return formatDateTime(value, fallback)
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days <= 7) return `${days} 天前`
  return formatDate(value, fallback)
}

/** 金额：1,234.00（不带符号，页面自己加 ¥）。 */
export function formatAmount(value, digits = 2) {
  const n = Number(value || 0)
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

/** 带符号金额：¥1,234.00。 */
export function formatMoney(value) {
  return `¥${formatAmount(value)}`
}

/** 文件大小：1.2 MB / 345 KB / 89 B。 */
export function formatFileSize(bytes) {
  const n = Number(bytes || 0)
  if (n <= 0) return '-'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}
