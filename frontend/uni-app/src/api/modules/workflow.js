import { http } from '@/utils/request'

/** 批次全链路审核进度 */
export const workflowProgress = (batchId) => http.get(`/workflow/progress/${batchId}`)
/** 我的审核待办（按环节分组） */
export const workflowTodo = () => http.get('/workflow/todo')
/** 提交后的"等待谁审核"提示 */
export const workflowNotice = (bizType, bizId) => http.get('/workflow/notice', { bizType, bizId })
