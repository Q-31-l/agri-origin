import { defineStore } from 'pinia'
import { login as loginApi, getProfile, logout as logoutApi } from '@/api'

const TOKEN_KEY = 'agri_token'
const USER_KEY = 'agri_user'
const PERM_KEY = 'agri_perms'

function readJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) || fallback
  } catch (e) {
    return fallback
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readJson(USER_KEY, null),
    permissions: readJson(PERM_KEY, [])
  }),
  getters: {
    roleCode: (state) => (state.user ? state.user.roleCode : ''),
    displayName: (state) => (state.user ? state.user.realName : ''),
    isAdmin: (state) => (state.user ? state.user.roleCode === 'ADMIN' : false)
  },
  actions: {
    hasPerm(perm) {
      if (!perm) return true
      if (this.isAdmin) return true
      return this.permissions.includes(perm)
    },
    async login(form) {
      const res = await loginApi(form)
      const data = res.data
      this.token = data.token
      this.user = data.user
      this.permissions = data.permissions || []
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      localStorage.setItem(PERM_KEY, JSON.stringify(this.permissions))
      return data
    },
    async reloadProfile() {
      const res = await getProfile()
      this.user = res.data
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return res.data
    },
    async logout() {
      try {
        if (this.token) await logoutApi()
      } catch (e) {
        /* 忽略退出异常 */
      }
      this.token = ''
      this.user = null
      this.permissions = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      localStorage.removeItem(PERM_KEY)
    }
  }
})
