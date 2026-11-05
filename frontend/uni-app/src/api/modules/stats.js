import { http } from '@/utils/request'

export const statsOverview = () => http.get('/stats/overview')
export const chainFailed = () => http.get('/stats/chain-failed')
