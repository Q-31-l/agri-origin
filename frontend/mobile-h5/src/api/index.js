import request from './request'

export const login = (data) => request.post('/auth/login', data)
export const getProfile = () => request.get('/auth/profile')

export const batchPage = (params) => request.get('/batch/page', { params })
export const batchOptions = (params) => request.get('/batch/options', { params })
export const batchDetail = (id) => request.get(`/batch/${id}`)

export const plantingPage = (params) => request.get('/planting/page', { params })
export const createPlanting = (data) => request.post('/planting', data)

export const harvestPage = (params) => request.get('/harvest/page', { params })
export const createHarvest = (data) => request.post('/harvest', data)

export const traceQuery = (code) => request.get(`/trace/${code}`)
export const traceVerify = (code) => request.get(`/trace/${code}/verify`)

export const uploadFile = (formData) =>
  request.post('/file/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })

export const dict = (type) => request.get(`/dict/${type}`)
export const chainStatus = () => request.get('/chain/status')
