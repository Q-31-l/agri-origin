/** 平台判定：所有端差异判断集中在这里 */

export const isH5 = (() => {
  // #ifdef H5
  return true
  // #endif
  // #ifndef H5
  return false
  // #endif
})()

export const isMp = (() => {
  // #ifdef MP
  return true
  // #endif
  // #ifndef MP
  return false
  // #endif
})()

export const isMpWeixin = (() => {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  // #ifndef MP-WEIXIN
  return false
  // #endif
})()

export const isApp = (() => {
  // #ifdef APP-PLUS
  return true
  // #endif
  // #ifndef APP-PLUS
  return false
  // #endif
})()

let sysInfo = null
export function sys() {
  if (!sysInfo) {
    try {
      sysInfo = uni.getSystemInfoSync()
    } catch (e) {
      sysInfo = { windowWidth: 375, windowHeight: 667, statusBarHeight: 0, platform: 'devtools' }
    }
  }
  return sysInfo
}

/**
 * PC 布局最小宽度（px）：≥768 视为宽屏（平板 / 桌面）。
 * 之前用 900，笔记本浏览器窗口未最大化或分屏时（<900）会被误判成手机端，
 * 导致"电脑上打开只有手机预览"。降到 768（标准平板断点）后，
 * 只要窗口宽度达到平板级别就进入 PC 管理台。
 */
export const PC_MIN_WIDTH = 768

const VIEW_MODE_KEY = 'agri_view_mode'

/**
 * 读取手动指定的视图模式：'auto' 按宽度自适应，'pc' 强制 PC，'mobile' 强制移动端。
 * 用于"三端互通预览"——PC 上切手机版、手机上切电脑版。
 */
export function getViewMode() {
  // #ifdef H5
  try {
    return localStorage.getItem(VIEW_MODE_KEY) || 'auto'
  } catch (e) {
    return 'auto'
  }
  // #endif
  // #ifndef H5
  return 'auto'
  // #endif
}

/** 设置手动视图模式（'auto' | 'pc' | 'mobile'），并刷新当前页 */
export function setViewMode(mode) {
  // #ifdef H5
  try {
    if (mode === 'auto') localStorage.removeItem(VIEW_MODE_KEY)
    else localStorage.setItem(VIEW_MODE_KEY, mode)
  } catch (e) {
    /* ignore */
  }
  // #endif
}

/** H5 宽屏判定：手动指定优先，否则 ≥768px 视为 PC 布局 */
export function isPcWidth() {
  // #ifdef H5
  const force = getViewMode()
  if (force === 'pc') return true
  if (force === 'mobile') return false
  return (window.innerWidth || sys().windowWidth) >= PC_MIN_WIDTH
  // #endif
  // #ifndef H5
  return false
  // #endif
}

/** 状态栏高度（自定义导航栏用） */
export function statusBarHeight() {
  return sys().statusBarHeight || 0
}

/** 平台中文名，用于日志与设置页展示 */
export function platformName() {
  if (isApp) {
    // #ifdef APP-PLUS
    return sys().platform === 'ios' ? 'iOS App' : 'Android App'
    // #endif
  }
  if (isMpWeixin) return '微信小程序'
  if (isH5) return isPcWidth() ? 'PC 网页端' : '移动 H5'
  return '未知平台'
}
