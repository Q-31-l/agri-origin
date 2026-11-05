/** 日期与文本格式化（三端统一，不依赖 dayjs，减少包体） */

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

export function toDate(v) {
  if (!v) return null
  if (v instanceof Date) return v
  if (typeof v === 'number') return new Date(v)
  // iOS 不支持 "2026-11-05 10:00:00"，统一替换为 /
  const s = String(v).replace(/-/g, '/').replace('T', ' ').replace(/\.\d+Z?$/, '')
  const d = new Date(s)
  return isNaN(d.getTime()) ? null : d
}

export function formatDate(v) {
  const d = toDate(v)
  if (!d) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function formatDateTime(v) {
  const d = toDate(v)
  if (!d) return '-'
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function formatTime(v) {
  const d = toDate(v)
  if (!d) return '-'
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 相对时间：刚刚 / 5 分钟前 / 今天 10:20 / 3 天前 */
export function fromNow(v) {
  const d = toDate(v)
  if (!d) return '-'
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`
  return formatDate(d)
}

export function formatQty(v, unit) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  const s = isNaN(n) ? String(v) : n.toFixed(2).replace(/\.00$/, '').replace(/(\.\d)0$/, '$1')
  return unit ? `${s} ${unit}` : s
}

export function maskPhone(p) {
  if (!p || p.length < 7) return p || '-'
  return p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/** 哈希缩略：前 8 后 6 */
export function shortHash(h) {
  if (!h) return '-'
  const s = String(h)
  if (s.length <= 18) return s
  return `${s.slice(0, 10)}…${s.slice(-6)}`
}

export function truncate(s, len = 40) {
  if (!s) return ''
  const str = String(s)
  return str.length > len ? str.slice(0, len) + '…' : str
}
