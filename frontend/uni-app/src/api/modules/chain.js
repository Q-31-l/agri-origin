import { http } from '@/utils/request'

export const chainPage = (params) => http.get('/chain/page', params)
export const chainVerifyByNo = (batchNo) => http.get(`/chain/verify/${batchNo}`)
export const chainStatus = () => http.get('/chain/status')

/** 把历史未上链批次补登记到链上（仅监管方 / 管理员） */
export const chainRepair = () => http.post('/chain/repair/batches', {})
