import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 20000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('agri_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(body)
    }
    return body
  },
  (error) => {
    const status = error.response && error.response.status
    const message = (error.response && error.response.data && error.response.data.message) || error.message
    if (status === 401) {
      localStorage.removeItem('agri_token')
      localStorage.removeItem('agri_user')
      ElMessage.error('登录状态已失效，请重新登录')
      router.replace('/login')
    } else if (status === 403) {
      ElMessage.error(message || '越权操作已被拦截')
    } else {
      ElMessage.error(message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
