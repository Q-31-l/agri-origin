let loadingCount = 0

export function toast(title, icon = 'none', duration = 2000) {
  if (!title) return
  const t = String(title)
  // 小程序 icon:none 时最多两行，过长改用 modal
  if (t.length > 40) {
    uni.showModal({ title: '提示', content: t, showCancel: false })
    return
  }
  uni.showToast({ title: t, icon, duration, mask: false })
}

export function success(title = '操作成功') {
  uni.showToast({ title, icon: 'success', duration: 1500 })
}

export function loading(title = '加载中') {
  loadingCount++
  if (loadingCount === 1) uni.showLoading({ title, mask: true })
}

export function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) uni.hideLoading()
}

/** 兜底：页面异常时强制关闭 loading，避免遮罩卡死 */
export function resetLoading() {
  loadingCount = 0
  uni.hideLoading()
}

export function confirm(content, title = '提示', confirmText = '确定') {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText,
      confirmColor: '#1f7a4d',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

/**
 * 要求输入一段文字（如撤销理由）。
 *
 * 撤销是会被追责的处置动作，"为什么撤"必须由操作人自己写清楚，
 * 不能提供默认值——默认值会让理由退化成一句复制粘贴的废话。
 *
 * @returns {Promise<string|null>} 取消或留空时返回 null
 */
export function askReason(title, placeholder = '请填写理由') {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: placeholder,
      confirmText: '确定',
      confirmColor: '#d93026',
      success: (r) => {
        if (!r.confirm) return resolve(null)
        const text = (r.content || '').trim()
        resolve(text || null)
      },
      fail: () => resolve(null)
    })
  })
}

export function vibrate() {  // #ifndef H5
  uni.vibrateShort({ fail: () => {} })
  // #endif
}
