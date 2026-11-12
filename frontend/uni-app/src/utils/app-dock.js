/**
 * 让 tab 页使用自绘的五格 Dock（App 同款），把原生 tabBar 收起来。
 *
 * H5：原生 tabBar 是 DOM 节点，全局样式 `body.agri-app-shell uni-tabbar{display:none}`
 *     已经处理，这里什么都不用做。
 * 小程序：原生 tabBar 由客户端绘制，样式改不到，只能每页显示时调 uni.hideTabBar。
 *     页面仍然是 tabBar 页面，switchTab 照旧可用 —— 只是那一条不再显示。
 */
import { onShow } from '@dcloudio/uni-app'
import { isAppUI } from '@/config/platform'

export function useHideTabBar() {
  // #ifndef H5
  onShow(() => {
    if (!isAppUI()) return
    try {
      const ret = uni.hideTabBar({ animation: false })
      if (ret && typeof ret.catch === 'function') ret.catch(() => {})
    } catch (e) {
      /* 某些基础库版本不支持，忽略：Dock 仍在，只是底部多一条原生 tabBar */
    }
  })
  // #endif
}
