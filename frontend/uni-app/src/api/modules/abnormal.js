import { http } from '@/utils/request'

export const abnormalPage = (params) => http.get('/abnormal/page', params)
export const disposeAbnormal = (id, data) => http.post(`/abnormal/${id}/dispose`, data)
export const abnormalTrace = (id) => http.get(`/abnormal/${id}/trace`)
export const closeBatch = (batchId) => http.post(`/abnormal/batch/${batchId}/close`)
