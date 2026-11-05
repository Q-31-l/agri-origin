import { http } from '@/utils/request'

export const salesPage = (params) => http.get('/sales/page', params)
export const salesByBatch = (batchId) => http.get(`/sales/batch/${batchId}`)
export const createSales = (data) => http.post('/sales', data)
export const auditSales = (id, data) => http.post(`/sales/${id}/audit`, data)
