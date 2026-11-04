import { defineStore } from 'pinia'
import { login as loginApi, getProfile } from '@/api'

const TOKEN_KEY = 'agri_h5_token'
const USER_KEY = 'agri_h5_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    roleCode: (state) => (state.user ? state.user.roleCode : ''),
    displayName: (state) => (state.user ? state.user.realName : '')
  },
  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.data.token
      this.user = res.data.user
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return res.data
    },
    async refresh() {
      const res = await getProfile()
      this.user = res.data
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return res.data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
