const PREFIX = 'agri_'

export const storage = {
  set(key, value, ttlSeconds) {
    const payload = {
      v: value,
      t: Date.now(),
      e: ttlSeconds ? Date.now() + ttlSeconds * 1000 : 0
    }
    try {
      uni.setStorageSync(PREFIX + key, payload)
    } catch (e) {
      console.warn('[storage] 写入失败，可能超出小程序容量限制', key, e)
    }
  },

  get(key, fallback = null) {
    try {
      const raw = uni.getStorageSync(PREFIX + key)
      if (!raw || typeof raw !== 'object' || !('v' in raw)) return raw === '' ? fallback : raw || fallback
      if (raw.e && Date.now() > raw.e) {
        uni.removeStorageSync(PREFIX + key)
        return fallback
      }
      return raw.v
    } catch (e) {
      return fallback
    }
  },

  remove(key) {
    try {
      uni.removeStorageSync(PREFIX + key)
    } catch (e) {
      /* ignore */
    }
  },

  /** 只清理登录相关，不动其他缓存 */
  clearAuth() {
    ;['token', 'token_exp', 'refresh_token', 'refresh_exp', 'user', 'perms'].forEach((k) =>
      uni.removeStorageSync(PREFIX + k)
    )
  }
}
