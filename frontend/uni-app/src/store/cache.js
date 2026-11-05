import { defineStore } from 'pinia'

/**
 * 列表缓存脏标记：写操作后标记失效，列表页 onShow 时据此决定是否重拉
 */
export const useCacheStore = defineStore('cache', {
  state: () => ({
    dirty: {},
    lastSyncAt: {}
  }),

  actions: {
    markDirty(key) {
      this.dirty[key] = true
    },
    markFresh(key) {
      this.dirty[key] = false
      this.lastSyncAt[key] = Date.now()
    },
    isDirty(key) {
      return !!this.dirty[key]
    },
    isStale(key, maxAgeMs = 5 * 60 * 1000) {
      const t = this.lastSyncAt[key]
      return !t || Date.now() - t > maxAgeMs
    },
    /** 写操作成功后调用，一次性标记多个列表 */
    invalidate(...keys) {
      keys.forEach((k) => {
        this.dirty[k] = true
      })
    },
    reset() {
      this.dirty = {}
      this.lastSyncAt = {}
    }
  }
})
