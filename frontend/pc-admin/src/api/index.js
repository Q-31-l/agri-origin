import request from './request'

/* ---------------- 认证 ---------------- */
export const login = (data) => request.post('/auth/login', data)
export const getProfile = () => request.get('/auth/profile')
export const logout = () => request.post('/auth/logout')
export const changePassword = (data) => request.put('/auth/password', data)

/* ---------------- 批次 ---------------- */
export const batchPage = (params) => request.get('/batch/page', { params })
export const batchOptions = (params) => request.get('/batch/options', { params })
export const batchDetail = (id) => request.get(`/batch/${id}`)
export const createBatch = (data) => request.post('/batch', data)
export const updateBatch = (id, data) => request.put(`/batch/${id}`, data)
export const auditBatch = (id, data) => request.post(`/batch/${id}/audit`, data)
export const verifyBatch = (id) => request.get(`/batch/${id}/verify`)

/* ---------------- 农事 ---------------- */
export const plantingPage = (params) => request.get('/planting/page', { params })
export const plantingByBatch = (batchId) => request.get(`/planting/batch/${batchId}`)
export const createPlanting = (data) => request.post('/planting', data)
export const auditPlanting = (id, data) => request.post(`/planting/${id}/audit`, data)

/* ---------------- 采收 ---------------- */
export const harvestPage = (params) => request.get('/harvest/page', { params })
export const createHarvest = (data) => request.post('/harvest', data)
export const auditHarvest = (id, data) => request.post(`/harvest/${id}/audit`, data)

/* ---------------- 质检 ---------------- */
export const qualityPage = (params) => request.get('/quality/page', { params })
export const qualityByBatch = (batchId) => request.get(`/quality/batch/${batchId}`)
export const createQuality = (data) => request.post('/quality', data)

/* ---------------- 物流 ---------------- */
export const logisticsPage = (params) => request.get('/logistics/page', { params })
export const createLogistics = (data) => request.post('/logistics', data)

/* ---------------- 销售 ---------------- */
export const salesPage = (params) => request.get('/sales/page', { params })
export const createSales = (data) => request.post('/sales', data)

/* ---------------- 异常 ---------------- */
export const abnormalPage = (params) => request.get('/abnormal/page', { params })
export const disposeAbnormal = (id, data) => request.post(`/abnormal/${id}/dispose`, data)
export const abnormalTrace = (id) => request.get(`/abnormal/${id}/trace`)
export const closeBatch = (batchId) => request.post(`/abnormal/batch/${batchId}/close`)

/* ---------------- 链 ---------------- */
export const chainPage = (params) => request.get('/chain/page', { params })
export const chainVerifyByNo = (batchNo) => request.get(`/chain/verify/${batchNo}`)
export const chainStatus = () => request.get('/chain/status')

/* ---------------- 公共溯源 ---------------- */
export const traceQuery = (code) => request.get(`/trace/${code}`)
export const traceVerify = (code) => request.get(`/trace/${code}/verify`)

/* ---------------- 文件 ---------------- */
export const uploadFile = (formData) =>
  request.post('/file/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const fileUrl = (filePath) => filePath || ''

/* ---------------- 统计 ---------------- */
export const statsOverview = () => request.get('/stats/overview')
export const chainFailed = () => request.get('/stats/chain-failed')

/* ---------------- 用户 ---------------- */
export const userPage = (params) => request.get('/user/page', { params })
export const userList = (params) => request.get('/user/list', { params })
export const createUser = (data) => request.post('/user', data)
export const updateUser = (id, data) => request.put(`/user/${id}`, data)
export const resetPassword = (id, password) =>
  request.put(`/user/${id}/password`, null, { params: { password } })

/* ---------------- 基础数据 ---------------- */
export const villageList = () => request.get('/village/list')
export const createVillage = (data) => request.post('/village', data)
export const farmPage = (params) => request.get('/farm/page', { params })
export const farmList = (params) => request.get('/farm/list', { params })
export const createFarm = (data) => request.post('/farm', data)
export const updateFarm = (id, data) => request.put(`/farm/${id}`, data)
export const productList = () => request.get('/product/list')
export const productPage = (params) => request.get('/product/page', { params })
export const createProduct = (data) => request.post('/product', data)
export const updateProduct = (id, data) => request.put(`/product/${id}`, data)
export const dict = (type) => request.get(`/dict/${type}`)
export const logPage = (params) => request.get('/log/page', { params })

/* ---------------- 更正 ---------------- */
export const createCorrection = (data) => request.post('/correction', data)
export const correctionPage = (params) => request.get('/correction/page', { params })
