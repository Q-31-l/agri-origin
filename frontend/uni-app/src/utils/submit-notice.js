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
  // 标题已经写明"XX已提交"，正文再把"已提交，"开头顶一遍就成了重复话术：
  // 标题「农事记录已提交」+ 正文「等待溪头村 王秀兰（村级管理员）审核」才是一句完整的话。
  const msg = d.message.indexOf('已提交，') === 0 ? d.message.slice(4) : d.message
  const content = d.nextStep ? msg + '\n\n' + d.nextStep : msg
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
