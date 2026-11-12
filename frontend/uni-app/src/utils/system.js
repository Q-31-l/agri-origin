/**
 * 系统 / 窗口信息读取的统一入口。
 *
 * 为什么单独抽出来：
 *   微信基础库 2.20.1 起 `wx.getSystemInfoSync` 已废弃，而 `uni.getSystemInfoSync()`
 *   在小程序端就是转发给它的，所以每调用一次，开发者工具 Console 就多一条
 *   「wx.getSystemInfoSync is deprecated」黄色警告。本项目有 4 处调用落在启动路径上，
 *   于是每次打开都刷几条黄字，看着像小程序坏了。
 *
 * 分流策略：
 *   · 小程序：优先 `wx.getWindowInfo()`（窗口宽高、像素比、状态栏、安全区），
 *     再用 `wx.getDeviceInfo()` 补 `platform`；任一新接口不存在（老基础库）就退回旧接口。
 *   · H5 / App：这两个端没有废弃问题，直接 `uni.getSystemInfoSync()`，行为与改动前完全一致。
 *
 * 返回对象与旧接口同名同义（windowWidth / windowHeight / screenWidth / statusBarHeight /
 * pixelRatio / safeArea / platform），调用方只需要换函数名，布局取值不变。
 */

/** 兜底值：任何端取不到信息时也不至于白屏 */
const FALLBACK = {
  windowWidth: 375,
  windowHeight: 667,
  screenWidth: 375,
  screenHeight: 667,
  statusBarHeight: 0,
  pixelRatio: 2,
  platform: 'unknown'
}

export function readWindowInfo() {
  // #ifdef MP-WEIXIN
  try {
    if (typeof wx !== 'undefined' && typeof wx.getWindowInfo === 'function') {
      const win = wx.getWindowInfo()
      if (win && win.windowWidth) {
        let dev = {}
        try {
          if (typeof wx.getDeviceInfo === 'function') dev = wx.getDeviceInfo() || {}
        } catch (e) {
          /* 设备信息拿不到不影响布局，platform 退化为兜底值 */
        }
        // dev 在前、win 在后：布局字段以 getWindowInfo 为准，platform 由 getDeviceInfo 补
        return Object.assign({}, FALLBACK, dev, win)
      }
    }
  } catch (e) {
    /* 新接口异常时静默回退，不影响首屏 */
  }
  // #endif

  try {
    return uni.getSystemInfoSync()
  } catch (e) {
    return Object.assign({}, FALLBACK)
  }
}
