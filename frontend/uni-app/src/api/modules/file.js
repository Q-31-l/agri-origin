import { http } from '@/utils/request'

/** 某条业务记录的实证材料（图片/视频/附件），公开可读 */
export const fileEvidence = (bizType, bizId) =>
  http.get('/file/evidence', { bizType, bizId }, { auth: false })
