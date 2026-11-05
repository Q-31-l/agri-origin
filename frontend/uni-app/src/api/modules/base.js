import { http } from '@/utils/request'

/* ---------------- 村庄 ---------------- */
export const villageList = () => http.get('/village/list')
export const createVillage = (data) => http.post('/village', data)

/* ---------------- 农场 ---------------- */
export const farmPage = (params) => http.get('/farm/page', params)
export const farmList = (params) => http.get('/farm/list', params)
export const createFarm = (data) => http.post('/farm', data)
export const updateFarm = (id, data) => http.put(`/farm/${id}`, data)

/* ---------------- 产品 ---------------- */
export const productList = () => http.get('/product/list')
export const productPage = (params) => http.get('/product/page', params)
export const createProduct = (data) => http.post('/product', data)
export const updateProduct = (id, data) => http.put(`/product/${id}`, data)

/* ---------------- 字典 / 日志 / 更正 ---------------- */
export const dict = (type) => http.get(`/dict/${type}`)
export const logPage = (params) => http.get('/log/page', params)
export const createCorrection = (data) => http.post('/correction', data)
export const correctionPage = (params) => http.get('/correction/page', params)
