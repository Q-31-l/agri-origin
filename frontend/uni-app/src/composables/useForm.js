import { ref } from 'vue'
import { toast } from '@/utils/ui'
import { showSubmitNotice } from '@/utils/submit-notice'

/**
 * 表单提交通用逻辑：防重复提交、统一异常、成功后回调
 *
 * options.noticeType：提交成功后自动弹出「已提交，等待谁审核」，
 * 取值 BATCH / PLANTING / HARVEST / QUALITY / LOGISTICS / SALES。
 * 提示放在 useForm 里而不是每个页面各写一遍，是为了保证六个提交入口
 * 的口径完全一致（谁提交、等谁审、通过后进哪一环）。
 *
 * @param {(form) => Promise} submitter
 */
export function useForm(submitter, options = {}) {
  const { onSuccess, successText = '提交成功', validate, noticeType } = options
  const submitting = ref(false)
  const error = ref('')

  async function submit(form) {
    if (submitting.value) return
    error.value = ''
    if (validate) {
      const msg = validate(form)
      if (msg) {
        error.value = msg
        toast(msg)
        return null
      }
    }
    submitting.value = true
    try {
      const res = await submitter(form)
      if (successText) uni.showToast({ title: successText, icon: 'success' })
      const createdId = res && res.data
      if (noticeType && createdId) await showSubmitNotice(noticeType, createdId)
      if (onSuccess) await onSuccess(res)
      return res
    } catch (e) {
      // request 层已 toast，这里只记录
      error.value = (e && e.message) || '提交失败'
      return null
    } finally {
      submitting.value = false
    }
  }

  return { submitting, error, submit }
}
