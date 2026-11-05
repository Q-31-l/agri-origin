import { TAB_PAGES, ROUTES } from '@/config/routes'

let lastNav = 0

function lock(gap = 300) {
  const now = Date.now()
  if (now - lastNav < gap) return false
  lastNav = now
  return true
}

export function buildUrl(path, params) {
  if (!params) return path
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => {
      const v = typeof params[k] === 'object' ? JSON.stringify(params[k]) : params[k]
      return `${k}=${encodeURIComponent(v)}`
    })
    .join('&')
  return qs ? `${path}?${qs}` : path
}

export function nav(path, params) {
  if (!lock()) return
  if (TAB_PAGES.includes(path)) return uni.switchTab({ url: path })
  uni.navigateTo({
    url: buildUrl(path, params),
    fail: (err) => {
      console.warn('[nav fail]', path, err)
      uni.showToast({ title: '页面打开失败', icon: 'none' })
    }
  })
}

export function redirect(path, params) {
  uni.redirectTo({ url: buildUrl(path, params) })
}

export function relaunch(path, params) {
  uni.reLaunch({ url: buildUrl(path, params) })
}

export function back(delta = 1) {
  const pages = getCurrentPages()
  if (pages.length <= delta) return uni.reLaunch({ url: ROUTES.HOME })
  uni.navigateBack({ delta })
}

/** 从 onLoad 的 options 里安全解析 JSON 参数 */
export function parseParam(options, key, fallback = null) {
  if (!options || !options[key]) return fallback
  const raw = decodeURIComponent(options[key])
  try {
    return JSON.parse(raw)
  } catch (e) {
    return raw
  }
}
