import { defineStore } from 'pinia'
import { sys } from '@/config/platform'

export const useAppStore = defineStore('app', {
  state: () => ({
    windowWidth: sys().windowWidth || 375,
    windowHeight: sys().windowHeight || 667,
    statusBarHeight: sys().statusBarHeight || 0,
    networkType: 'unknown',
    isPc: false,
    launchAt: Date.now()
  }),

  getters: {
    /** 自定义导航栏总高度（px）：状态栏 + 44px 标题栏 */
    navBarHeight: (s) => (s.statusBarHeight || 0) + 44,
    isOffline: (s) => s.networkType === 'none'
  },

  actions: {
    init() {
      this.refreshSize()
      this.watchNetwork()
      // #ifdef H5
      window.addEventListener('resize', this.refreshSize)
      // #endif
    },

    refreshSize() {
      try {
        const info = uni.getSystemInfoSync()
        this.windowWidth = info.windowWidth
        this.windowHeight = info.windowHeight
        this.statusBarHeight = info.statusBarHeight || 0
        // #ifdef H5
        this.isPc = (window.innerWidth || info.windowWidth) >= 900
        // #endif
        // #ifndef H5
        this.isPc = false
        // #endif
      } catch (e) {
        /* ignore */
      }
    },

    watchNetwork() {
      uni.getNetworkType({
        success: (r) => {
          this.networkType = r.networkType
        }
      })
      uni.onNetworkStatusChange((r) => {
        this.networkType = r.networkType
        if (!r.isConnected) {
          uni.showToast({ title: '网络已断开', icon: 'none' })
        }
      })
    }
  }
})
