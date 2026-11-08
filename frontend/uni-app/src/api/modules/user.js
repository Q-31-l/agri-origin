import { http } from '@/utils/request'

export const userPage = (params) => http.get('/user/page', params)
export const userList = (params) => http.get('/user/list', params)

/** 下拉选项：只返回 id/姓名，生产主体建农场时用这个，不需要 user:list */
export const userOptions = (params) => http.get('/user/options', params)
export const createUser = (data) => http.post('/user', data)
export const updateUser = (id, data) => http.put(`/user/${id}`, data)
export const resetPassword = (id, password) =>
  http.put(`/user/${id}/password`, null, { params: { password } })

/* ---------------- 注册审核（仅系统管理员） ---------------- */
/** 待审核的注册申请（村级管理员/监管人员） */
export const pendingUsers = () => http.get('/user/pending')
/** 审核注册申请：approve=true 通过 / false 拒绝 */
export const auditUser = (id, approve) =>
  http.post(`/user/${id}/audit`, null, { params: { approve } })
