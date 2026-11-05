import { useUserStore } from '@/store/user'
import { ROUTES } from '@/config/routes'

/** 页面级登录校验：未登录直接跳登录页，返回 false */
export function requireLogin() {
  const store = useUserStore()
  if (store.token) return true
  uni.reLaunch({ url: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(currentPath())}` })
  return false
}

/** 页面级权限校验：无权限提示并返回 false（不跳转，留在当前页） */
export function requirePerm(perm, silent = false) {
  const store = useUserStore()
  if (!perm || store.hasPerm(perm)) return true
  if (!silent) {
    uni.showToast({ title: '无权访问该功能', icon: 'none' })
  }
  return false
}

/** 是否可进入某页面（用于列表按钮显隐） */
export function can(perm) {
  try {
    return useUserStore().hasPerm(perm)
  } catch (e) {
    return false
  }
}

export function currentPath() {
  const pages = getCurrentPages()
  if (!pages.length) return ''
  const p = pages[pages.length - 1]
  return '/' + (p.route || '')
}
