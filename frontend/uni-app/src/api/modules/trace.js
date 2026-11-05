import { http } from '@/utils/request'

/** 公开溯源（免登录） */
export const traceQuery = (code) => http.get(`/trace/${encodeURIComponent(code)}`, null, { auth: false })
export const traceVerify = (code) => http.get(`/trace/${encodeURIComponent(code)}/verify`, null, { auth: false })
