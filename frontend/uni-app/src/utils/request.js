import { ENV } from '@/config/env'
import { getToken, clearAuth } from '@/utils/auth'
import { refreshAccessToken } from '@/utils/token'
import { toast } from '@/utils/ui'
import { messageOf } from '@/utils/errors'

let loadingCount = 0
const pending = new Map()
let redirecting = false

function startLoading() {
  if (loadingCount++ === 0) uni.showLoading({ title: '加载中', mask: true })
}
function endLoading() {
  if (loadingCount > 0 && --loadingCount === 0) uni.hideLoading()
}

function requestKey(config) {
  return [config.method, config.url, JSON.stringify(config.data || {})].join('|')
}

export function buildQuery(params) {
  if (!params) return ''
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => {
      const v = typeof params[k] === 'object' ? JSON.stringify(params[k]) : params[k]
      return `${k}=${encodeURIComponent(v)}`
    })
    .join('&')
  return qs ? `?${qs}` : ''
}

function handleUnauthorized() {
  if (redirecting) return
  redirecting = true
  clearAuth()
  uni.$emit('auth:expired')
  toast('登录状态已失效，请重新登录')
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/auth/login' })
    redirecting = false
  }, 500)
}

export function request(options) {
  const {
    url,
    method = 'GET',
    data,
    params,
    header = {},
    loading: useLoading = false,
    auth = true,
    dedupe = true,
    timeout = 20000,
    retry = 0
  } = options

  let fullUrl
  if (/^https?:\/\//.test(url)) {
    fullUrl = url + buildQuery(params)
  } else {
    fullUrl = ENV.baseURL + url + buildQuery(params)
  }

  const key = requestKey({ method, url: fullUrl, data })
  if (dedupe && pending.has(key)) return pending.get(key)
  if (useLoading) startLoading()

  const promise = new Promise((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data: method === 'GET' ? undefined : data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(auth && getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
        ...header
      },
      success(res) {
        const { statusCode, data: body } = res
        const bizCode = body && typeof body.code !== 'undefined' ? body.code : null
        const msg = (body && body.message) || ''

        // 后端业务异常返回 HTTP 400 + body.code=4001/5001/5002，必须先看业务码
        if (statusCode === 401 || bizCode === 401) {
          // auth:false 的请求（账号登录 / 微信登录 / 续期）本来就不使用会话，
          // 这里的 401 是"账号或密码错误"，不能当成登录态失效去跳登录页
          if (options.auth === false) {
            const text = msg || '账号或密码错误'
            toast(text)
            return reject({ code: 401, message: text })
          }
          // 已经续期过一次仍然 401，说明账号被禁用或权限被收回，直接登出
          if (options.skipRefresh) {
            handleUnauthorized()
            return reject({ code: 401, message: '登录已失效' })
          }
          // 先用 refresh token 静默换发，再重放原请求；换不到才跳登录
          return refreshAccessToken().then((ok) => {
            if (!ok) {
              handleUnauthorized()
              return reject({ code: 401, message: '登录已失效' })
            }
            return request({ ...options, skipRefresh: true, dedupe: false }).then(resolve, reject)
          })
        }
        if (statusCode === 403 || bizCode === 403) {
          toast(msg || '越权操作已被拦截')
          return reject({ code: 403, message: msg || '无权限' })
        }
        if (bizCode !== null) {
          if (bizCode === 0) return resolve(body)
          const text = msg || messageOf(bizCode) || '操作失败'
          toast(text)
          return reject({ code: bizCode, message: text, httpStatus: statusCode })
        }
        if (statusCode < 200 || statusCode >= 300) {
          const text = msg || `服务异常（${statusCode}）`
          toast(text)
          return reject({ code: statusCode, message: text })
        }
        resolve(body)
      },
      fail(err) {
        if (retry > 0) {
          pending.delete(key)
          return request({ ...options, retry: retry - 1 }).then(resolve, reject)
        }
        const text = /timeout/i.test(err.errMsg || '') ? '请求超时，请检查网络' : '网络连接失败，请稍后重试'
        toast(text)
        if (ENV.debug) console.error('[request fail]', fullUrl, err)
        reject({ code: -1, message: text, raw: err })
      },
      complete() {
        if (useLoading) endLoading()
        pending.delete(key)
      }
    })
  })

  if (dedupe) pending.set(key, promise)
  return promise
}

export const http = {
  get: (url, params, opt = {}) => request({ url, method: 'GET', params, ...opt }),
  post: (url, data, opt = {}) => request({ url, method: 'POST', data, ...opt }),
  put: (url, data, opt = {}) => request({ url, method: 'PUT', data, ...opt }),
  del: (url, params, opt = {}) => request({ url, method: 'DELETE', params, ...opt })
}

export default request
