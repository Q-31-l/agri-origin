import { http } from '@/utils/request'

export const plantingPage = (params) => http.get('/planting/page', params)
export const plantingByBatch = (batchId) => http.get(`/planting/batch/${batchId}`)
export const createPlanting = (data) => http.post('/planting', data)
export const auditPlanting = (id, data) => http.post(`/planting/${id}/audit`, data)
