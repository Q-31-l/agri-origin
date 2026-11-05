import { ENV } from '@/config/env'
import { getRefreshToken, hasRefreshToken, setToken, setRefreshToken, clearAuth } from '@/utils/auth'

/**
 * 访问令牌续期。
 *
 * 设计要点：
 * 1. 单飞（single-flight）：页面同时发 6 个请求一起 401 时，只发一次 /auth/refresh，
 *    其余请求等同一个 Promise，避免把 refresh token 一次性用光。
 * 2. 不走 request.js：这里要绕开"401 再触发续期"的死循环，直接用原生 uni.request。
 * 3. 失败即清理：refresh 也过期/失效时立刻清空本地凭证，由调用方跳登录页。
 */
let refreshing = null

export function refreshAccessToken() {
  if (refreshing) return refreshing

  if (!hasRefreshToken()) return Promise.resolve(false)

  const refreshToken = getRefreshToken()

  refreshing = new Promise((resolve) => {
    uni.request({
      url: ENV.baseURL + '/auth/refresh',
      method: 'POST',
      data: { refreshToken },
      timeout: 15000,
      header: { 'Content-Type': 'application/json' },
      success(res) {
        const body = res.data || {}
        const data = body.data
        if (res.statusCode === 200 && body.code === 0 && data && data.token) {
          setToken(data.token, data.expiresIn)
          if (data.refreshToken) setRefreshToken(data.refreshToken, data.refreshExpiresIn)
          // 通知 store 同步内存态（token / 用户信息 / 权限可能已被管理员改动）
          uni.$emit('auth:refreshed', data)
          resolve(true)
          return
        }
        if (ENV.debug) console.warn('[refresh] 续期失败', res.statusCode, body && body.message)
        resolve(false)
      },
      fail(err) {
        if (ENV.debug) console.warn('[refresh] 网络失败', err && err.errMsg)
        resolve(false)
      }
    })
  }).then(
    (ok) => {
      refreshing = null
      if (!ok) clearAuth()
      return ok
    },
    () => {
      refreshing = null
      clearAuth()
      return false
    }
  )

  return refreshing
}
