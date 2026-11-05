import { http } from '@/utils/request'

export const chainPage = (params) => http.get('/chain/page', params)
export const chainVerifyByNo = (batchNo) => http.get(`/chain/verify/${batchNo}`)
export const chainStatus = () => http.get('/chain/status')
