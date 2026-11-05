import { ref } from 'vue'
import { toast } from '@/utils/ui'

/**
 * 表单提交通用逻辑：防重复提交、统一异常、成功后回调
 * @param {(form) => Promise} submitter
 */
export function useForm(submitter, options = {}) {
  const { onSuccess, successText = '提交成功', validate } = options
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
