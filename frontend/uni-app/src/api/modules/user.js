import { http } from '@/utils/request'

export const userPage = (params) => http.get('/user/page', params)
export const userList = (params) => http.get('/user/list', params)
export const createUser = (data) => http.post('/user', data)
export const updateUser = (id, data) => http.put(`/user/${id}`, data)
export const resetPassword = (id, password) =>
  http.put(`/user/${id}/password`, null, { params: { password } })
