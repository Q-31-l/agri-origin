import { http } from '@/utils/request'

export const harvestPage = (params) => http.get('/harvest/page', params)
export const harvestByBatch = (batchId) => http.get(`/harvest/batch/${batchId}`)
export const createHarvest = (data) => http.post('/harvest', data)
export const auditHarvest = (id, data) => http.post(`/harvest/${id}/audit`, data)
