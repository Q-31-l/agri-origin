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
/** 侧栏布局能舒服容纳表格的最小宽度（仅作样式断点参考，判断逻辑见 isPcWidth） */
export const PC_MIN_WIDTH = 900

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
 * 是否平板设备（H5）。
 *
 * 平板和手机的区别不在于"能不能触摸"，而在于**竖屏下够不够放侧栏 + 表格**：
 * iPad 竖屏只有 768px，塞进 216px 侧栏后剩 552px，批次表格会挤成一团。
 * 所以平板单独判断：竖屏走移动版，横屏（≥1024）才进管理台。
 */
function isTablet() {
  // #ifdef H5
  try {
    const ua = navigator.userAgent || ''
    if (/iPad/i.test(ua)) return true
    // iPadOS 13+ 的 UA 伪装成桌面 Safari（Macintosh），靠触点数兜底
    if (/Macintosh/i.test(ua) && (navigator.maxTouchPoints || 0) > 1) return true
    // 安卓平板：UA 带 Android 但不带 Mobile
    if (/Android/i.test(ua) && !/Mobile/i.test(ua)) return true
    return false
  } catch (e) {
    return false
  }
  // #endif
  // #ifndef H5
  return false
  // #endif
}

/**
 * H5 宽屏判定 —— 按「设备类型 + 窗口宽度」双重判断，不做一刀切：
 *
 * | 设备            | 判定                                   |
 * |-----------------|----------------------------------------|
 * | 手机            | 永远移动版（哪怕横屏 900px）            |
 * | 平板            | 竖屏移动版，横屏 ≥1024 才进管理台       |
 * | 桌面浏览器      | ≥900 进管理台，再窄走移动版（窄窗更好用）|
 *
 * 这样 iPad 竖屏拿到的是为触屏设计的卡片流，而不是被压扁的表格；
 * 手机上无论怎么转屏都不会突然变成电脑界面。
 */
export function isPcWidth() {
  // #ifdef H5
  if (isTouchPhone()) return false
  const w = window.innerWidth || sys().windowWidth
  if (isTablet()) return w >= 1024
  return w >= 900
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
