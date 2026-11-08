import { defineStore } from 'pinia'
import {
  login as loginApi,
  register as registerApi,
  getProfile,
  logout as logoutApi,
  wxLogin as wxLoginApi,
  wxAppLogin as wxAppLoginApi,
  wxBind as wxBindApi
} from '@/api'
import {
  getToken,
  setToken,
  setRefreshToken,
  getUser,
  setUser,
  getPerms,
  setPerms,
  clearAuth
} from '@/utils/auth'
import { socket } from '@/utils/socket'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    user: getUser(),
    permissions: getPerms(),
    lastSyncAt: 0
  }),

  getters: {
    roleCode: (s) => (s.user ? s.user.roleCode : ''),
    roleName: (s) => (s.user ? s.user.roleName : ''),
    displayName: (s) => (s.user ? s.user.realName : ''),
    orgName: (s) => (s.user ? s.user.orgName || s.user.villageName || s.user.farmName || '' : ''),
    isAdmin: (s) => (s.user ? s.user.roleCode === 'ADMIN' : false),
    isLogin: (s) => !!s.token
  },

  actions: {
    hasPerm(perm) {
      if (!perm) return true
      if (this.isAdmin) return true
      return this.permissions.includes(perm)
    },

    applyAuth(data) {
      this.token = data.token
      this.user = data.user
      this.permissions = data.permissions || []
      this.lastSyncAt = Date.now()
      setToken(data.token, data.expiresIn)
      // 三端一致：refresh token 也落本地，access 过期后无需用户重新登录
      setRefreshToken(data.refreshToken, data.refreshExpiresIn)
      setUser(data.user)
      setPerms(this.permissions)
      // 登录成功后立即建立实时通知通道：账号密码 / 微信登录 / 微信绑定三条路径都走这里
      socket.connect()
    },

    /**
     * 静默续期成功：只覆盖凭证与权限，不动业务缓存。
     * 由 App.vue 监听 auth:refreshed 事件后调用。
     */
    applyRefreshed(data) {
      if (!data || !data.token) return
      this.token = data.token
      if (data.user) {
        this.user = data.user
        setUser(data.user)
      }
      if (data.permissions) {
        this.permissions = data.permissions
        setPerms(this.permissions)
      }
      this.lastSyncAt = Date.now()
    },

    async login(form) {
      const res = await loginApi(form)
      this.applyAuth(res.data)
      return res.data
    },

    /**
     * 自助注册：后端注册成功即签发 JWT，这里直接落凭证，
     * 用户不用"注册完再输一遍账号密码"。
     * 例外：村级管理员/监管人员注册后进入待审核（pendingAudit=true），
     * 此时不落凭证，由页面提示"等待系统管理员审核"。
     */
    async register(form) {
      const res = await registerApi(form)
      if (res.data && res.data.pendingAudit) return res.data
      this.applyAuth(res.data)
      return res.data
    },

    /** 小程序微信一键登录：返回 needBind 时由页面跳绑定 */
    async wxLogin(code) {
      const res = await wxLoginApi(code)
      if (res.data.needBind) return res.data
      this.applyAuth(res.data)
      return res.data
    },

    /**
     * App 端微信登录（开放平台）。
     *
     * uni.login 在 App 端的返回形态随 SDK 版本不同：
     * 新版给 code（推荐，走 oauth2/access_token），老版给 authResult.access_token + openid。
     * 这里统一打包成一个 payload，由后端按有无 code 分别处理。
     */
    async wxAppLogin(loginRes = {}) {
      const auth = loginRes.authResult || {}
      const payload = {
        code: loginRes.code || auth.code || '',
        openid: loginRes.openid || auth.openid || '',
        accessToken: loginRes.accessToken || loginRes.access_token || auth.access_token || ''
      }
      if (!payload.code && !(payload.openid && payload.accessToken)) {
        throw { code: -2, message: '微信授权信息不完整' }
      }
      const res = await wxAppLoginApi(payload)
      if (res.data.needBind) return res.data
      this.applyAuth(res.data)
      return res.data
    },

    async wxBind(form) {
      const res = await wxBindApi(form)
      this.applyAuth(res.data)
      return res.data
    },

    async reloadProfile() {
      const res = await getProfile()
      this.user = res.data
      setUser(this.user)
      this.lastSyncAt = Date.now()
      return res.data
    },

    /** 冷启动恢复：有 token 则静默刷新用户信息，保证多端一致 */
    async restore() {
      if (!this.token) return
      try {
        await this.reloadProfile()
      } catch (e) {
        /* 失效由 request 统一处理 */
      }
    },

    async logout() {
      try {
        if (this.token) await logoutApi()
      } catch (e) {
        /* 忽略 */
      }
      this.reset()
    },

    reset() {
      this.token = ''
      this.user = null
      this.permissions = []
      this.lastSyncAt = 0
      clearAuth()
      socket.close()
    }
  }
})
