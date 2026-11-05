import { ENV } from '@/config/env'

/** 后端返回的 filePath 形如 /file/download/12，需补 origin（不能带 /api） */
export function resolveFileUrl(filePath) {
  if (!filePath) return ''
  const p = String(filePath)
  if (/^(https?:|blob:|wxfile:|file:)/.test(p)) return p
  const origin = ENV.origin
  return origin + (p.startsWith('/') ? p : '/' + p)
}

/** 取文件名后缀 */
export function extOf(name) {
  if (!name) return ''
  const m = String(name).match(/\.([a-zA-Z0-9]+)$/)
  return m ? m[1].toLowerCase() : ''
}

export function isImage(name) {
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extOf(name))
}

export function isPdf(name) {
  return extOf(name) === 'pdf'
}

/** 人眼可读的文件大小 */
export function fileSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  const n = Number(bytes)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}
