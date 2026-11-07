import { http } from '@/utils/request'

/* ---------------- 认证 ---------------- */

// 登录类接口一律 auth:false：
// 1) 登录时不该带上一个可能已过期的旧 token；
// 2) 这样 401 会被 request.js 当成"账号或密码错误"提示，而不是"登录已失效"跳转。
export const login = (data) => http.post('/auth/login', data, { auth: false })
// 注册同样 auth:false：此时还没有任何凭证，带上旧 token 只会让 401 被误判成"登录已失效"
export const register = (data) => http.post('/auth/register', data, { auth: false })
// 注册页的"所属村"下拉：未登录就要能取到
export const publicVillages = () => http.get('/public/villages', null, { auth: false })
export const getProfile = () => http.get('/auth/profile')
export const logout = () => http.post('/auth/logout')
export const changePassword = (data) => http.put('/auth/password', data)

/* ---------------- 微信登录（小程序） ---------------- */
export const wxLogin = (code) => http.post('/auth/wx-login', { code }, { auth: false })
export const wxBind = (data) => http.post('/auth/wx-bind', data, { auth: false })

/* ---------------- 微信登录（App / 开放平台） ---------------- */
export const wxAppLogin = (payload) => http.post('/auth/wx-app-login', payload, { auth: false })

/* ---------------- 微信绑定管理 ---------------- */
export const wxUnbind = (platform) =>
  http.post(`/auth/wx-unbind${platform ? `?platform=${platform}` : ''}`)
export const wxStatus = (platform) =>
  http.get(`/auth/wx-status${platform ? `?platform=${platform}` : ''}`)
