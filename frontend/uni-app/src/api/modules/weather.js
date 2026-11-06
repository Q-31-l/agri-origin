import { http } from '@/utils/request'

/** 当日天气：坐标缺省时后端按请求 IP 粗略定位兜底；取不到返回 null，前端手选 */
export const currentWeather = (params) => http.get('/weather/current', params)
