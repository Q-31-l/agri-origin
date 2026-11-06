import { http } from '@/utils/request'

/** 公开溯源（免登录） */
export const traceQuery = (code) => http.get(`/trace/${encodeURIComponent(code)}`, null, { auth: false })
export const traceVerify = (code) => http.get(`/trace/${encodeURIComponent(code)}/verify`, null, { auth: false })

/**
 * 示例溯源码（免登录）
 * 不能用 /batch/page 代替：那个接口要登录，未登录访客拿到 401 会被
 * 请求拦截器整页踢到登录页，公开的溯源页就废了。
 */
export const traceSamples = (limit = 3) => http.get('/trace/samples', { limit }, { auth: false })
