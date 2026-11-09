import { workflowNotice } from '@/api'

/**
 * 提交成功后弹「已提交，等待谁审核」。
 *
 * 只提示"提交成功"会让提交方以为流程结束了；这里把下一步说清楚：
 * 现在在等谁（村管/监管，带姓名）、通过之后进入哪一环节。
 * 接口取不到时静默降级，不影响提交流程本身。
 *
 * @param {string} bizType BATCH / PLANTING / HARVEST / QUALITY / LOGISTICS / SALES
 * @param {number|string} bizId 业务记录 id
 */
export async function showSubmitNotice(bizType, bizId) {
  if (!bizType || !bizId) return
  let d = null
  try {
    const res = await workflowNotice(bizType, bizId)
    d = (res && res.data) || null
  } catch (e) {
    return
  }
  if (!d || !d.message) return
  const content = d.nextStep ? d.message + '\n\n' + d.nextStep : d.message
  await new Promise((resolve) => {
    uni.showModal({
      title: d.title || '提交成功',
      content,
      showCancel: false,
      confirmText: '知道了',
      success: () => resolve(),
      fail: () => resolve()
    })
  })
}
