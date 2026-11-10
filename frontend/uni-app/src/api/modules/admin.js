import { http } from '@/utils/request'

/**
 * 管理员兜底处置。
 *
 * 撤销不是删除：业务记录在链下标记失效，链上存证保留。
 * 因此这里统一用 POST 表达"处置动作"，而不是 REST 的 DELETE。
 */

/** 撤销一条业务记录：bizType = PLANTING/HARVEST/QUALITY/LOGISTICS/SALES */
export const revokeRecord = (data) => http.post('/admin/revoke', data)

/** 撤销单张实证文件（不合规照片、无关截图等） */
export const revokeFile = (id, reason) =>
  http.post(`/admin/file/${id}/revoke`, null, { params: { reason } })
