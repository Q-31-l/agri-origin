import { http } from '@/utils/request'

export const qualityPage = (params) => http.get('/quality/page', params)
export const qualityByBatch = (batchId) => http.get(`/quality/batch/${batchId}`)
export const createQuality = (data) => http.post('/quality', data)
