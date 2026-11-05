import { http } from '@/utils/request'

export const logisticsPage = (params) => http.get('/logistics/page', params)
export const logisticsByBatch = (batchId) => http.get(`/logistics/batch/${batchId}`)
export const createLogistics = (data) => http.post('/logistics', data)
export const auditLogistics = (id, data) => http.post(`/logistics/${id}/audit`, data)
