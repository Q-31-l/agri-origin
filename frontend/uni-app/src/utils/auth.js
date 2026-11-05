import { storage } from './storage'

const TOKEN_KEY = 'token'
const TOKEN_EXP_KEY = 'token_exp'
const REFRESH_KEY = 'refresh_token'
const REFRESH_EXP_KEY = 'refresh_exp'
const USER_KEY = 'user'
const PERM_KEY = 'perms'

export const getToken = () => storage.get(TOKEN_KEY, '')
export const getRefreshToken = () => storage.get(REFRESH_KEY, '')
export const getUser = () => storage.get(USER_KEY, null)
export const getPerms = () => storage.get(PERM_KEY, [])

export function setToken(token, expiresIn) {
  storage.set(TOKEN_KEY, token)
  if (expiresIn) storage.set(TOKEN_EXP_KEY, Date.now() / 1000 + Number(expiresIn))
}

/**
 * 刷新令牌。
 *
 * 单独存一个 key，并带自己的过期时间——refresh token 比 access token 长得多，
 * 混用同一个 exp 会导致「access 还剩 1 小时，却把 refresh 也当成过期」。
 */
export function setRefreshToken(refreshToken, expiresIn) {
  if (!refreshToken) return
  storage.set(REFRESH_KEY, refreshToken)
  if (expiresIn) storage.set(REFRESH_EXP_KEY, Date.now() / 1000 + Number(expiresIn))
}

export const setUser = (u) => storage.set(USER_KEY, u)
export const setPerms = (p) => storage.set(PERM_KEY, p)

/** token 是否即将过期（默认提前 30 分钟） */
export function isTokenExpiringSoon(thresholdSec = 1800) {
  const exp = storage.get(TOKEN_EXP_KEY, 0)
  return exp > 0 && exp - Date.now() / 1000 < thresholdSec
}

/** refresh token 是否还在有效期内（本地粗判，真正的校验在服务端） */
export function hasRefreshToken() {
  const token = getRefreshToken()
  if (!token) return false
  const exp = storage.get(REFRESH_EXP_KEY, 0)
  return !exp || exp > Date.now() / 1000
}

export const clearAuth = () => storage.clearAuth()
