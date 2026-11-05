import { http } from '@/utils/request'

export const batchPage = (params) => http.get('/batch/page', params)
export const batchOptions = (params) => http.get('/batch/options', params)
export const batchDetail = (id) => http.get(`/batch/${id}`)
export const createBatch = (data) => http.post('/batch', data)
export const updateBatch = (id, data) => http.put(`/batch/${id}`, data)
export const auditBatch = (id, data) => http.post(`/batch/${id}/audit`, data)
export const verifyBatch = (id) => http.get(`/batch/${id}/verify`)
