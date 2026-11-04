import axios from 'axios'
import { showFailToast } from 'vant'
import router from '@/router'

const request = axios.create({ baseURL: '/api', timeout: 20000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('agri_h5_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      showFailToast(body.message || '操作失败')
      return Promise.reject(body)
    }
    return body
  },
  (error) => {
    const status = error.response && error.response.status
    const message = (error.response && error.response.data && error.response.data.message) || '网络异常'
    if (status === 401) {
      localStorage.removeItem('agri_h5_token')
      localStorage.removeItem('agri_h5_user')
      router.replace('/login')
    }
    showFailToast(message)
    return Promise.reject(error)
  }
)

export default request
