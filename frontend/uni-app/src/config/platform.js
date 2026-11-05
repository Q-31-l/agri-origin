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

/**
 * 是否手机设备（H5）：靠 UA 区分「手机」与「桌面 / 平板」。
 * 只在明确是手机（iPhone / Android 手机 / iPod）时返回 true，
 * Android 平板 UA 无 `Mobile` 关键字，会落入宽度判断，与桌面一致。
 */
function isTouchPhone() {
  // #ifdef H5
  try {
    const ua = navigator.userAgent || ''
    return /iPhone|iPod|Android[\s\S]*Mobile/i.test(ua)
  } catch (e) {
    return false
  }
  // #endif
  // #ifndef H5
  return false
  // #endif
}

/**
 * H5 宽屏判定 —— 纯自动自适应，无任何手动覆盖：
 * 1. 手机设备（iPhone / Android 手机）→ 永远移动版；
 * 2. 其余（桌面 / 平板）→ 按窗口宽度，≥768px 视为 PC 布局。
 *
 * 效果：手机上打开就是手机版，电脑上打开就是电脑版，
 * 桌面浏览器拖宽 / 拖窄窗口还会实时在两端之间自动切换。
 */
export function isPcWidth() {
  // #ifdef H5
  if (isTouchPhone()) return false
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
