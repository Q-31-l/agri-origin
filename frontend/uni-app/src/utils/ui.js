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

export function vibrate() {
  // #ifndef H5
  uni.vibrateShort({ fail: () => {} })
  // #endif
}
