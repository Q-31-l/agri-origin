import { onUnload } from '@dcloudio/uni-app'

/**
 * 订阅三端实时同步事件。
 *
 * 事件由 App.vue 的 WebSocket 监听统一抛出（名称为 realtime:changed），
 * payload 形如 { type: 'batch'|'abnormal'|'chain', batchNo, action, message, timestamp }。
 *
 * @param {(msg) => void} handler 收到事件时的回调
 * @param {{ types?: string[] }} options 只关心的事件类型
 * @returns {() => void} 手动取消订阅
 */
export function useRealtime(handler, options = {}) {
  const types = options.types || ['batch', 'abnormal', 'chain']
  const listener = (msg) => {
    if (!msg || !types.includes(msg.type)) return
    handler(msg)
  }
  uni.$on('realtime:changed', listener)
  onUnload(() => uni.$off('realtime:changed', listener))
  return () => uni.$off('realtime:changed', listener)
}
