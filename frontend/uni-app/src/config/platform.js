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

/** H5 宽屏判定：≥900px 视为 PC 布局 */
export function isPcWidth() {
  // #ifdef H5
  return (window.innerWidth || sys().windowWidth) >= 900
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
