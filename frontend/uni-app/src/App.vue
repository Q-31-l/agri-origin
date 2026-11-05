<script>
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useCacheStore } from '@/store/cache'
import { socket } from '@/utils/socket'
import { isTokenExpiringSoon } from '@/utils/auth'
import { refreshAccessToken } from '@/utils/token'

/**
 * 三端实时同步：任一端写入批次 / 异常处置 / 上链存证后，
 * 服务端通过 /api/ws 广播事件，其他端收到后把相关列表缓存标记为脏，
 * 并抛出全局事件 realtime:changed，正在展示的列表页可据此立即刷新。
 */
function bindRealtime() {
  const handler = (msg) => {
    if (!msg || msg.type === 'open') return
    useCacheStore().invalidate(
      'batch', 'batch:list', 'batch:detail', 'abnormal',
      'quality', 'planting', 'harvest', 'logistics', 'sales'
    )
    uni.$emit('realtime:changed', msg)
  }
  socket.on('batch', handler)
  socket.on('abnormal', handler)
  socket.on('chain', handler)
}

export default {
  onLaunch() {
    const app = useAppStore()
    app.init()

    const user = useUserStore()
    // 冷启动静默刷新用户信息，保证多端数据一致
    if (user.token) user.restore()

    // 请求层 401 时清空内存态
    uni.$on('auth:expired', () => {
      useUserStore().reset()
      useCacheStore().reset()
      socket.close()
    })

    // 静默续期成功：把新 token / 用户信息 / 权限同步进内存态
    uni.$on('auth:refreshed', (data) => useUserStore().applyRefreshed(data))

    bindRealtime()

    // #ifdef MP-WEIXIN
    if (typeof wx !== 'undefined' && wx.getPrivacySetting) {
      wx.getPrivacySetting({
        success: (r) => {
          if (r.needAuthorization) {
            uni.navigateTo({ url: '/pages-mp/privacy/index' })
          }
        }
      })
    }
    // #endif
  },

  onShow() {
    const user = useUserStore()
    if (!user.token) return
    socket.connect()
    // 临近过期先静默续期：比等第一个请求 401 再补要平滑，用户看不到任何中断
    if (isTokenExpiringSoon()) refreshAccessToken()
  },

  onHide() {
    socket.close()
  },

  onError(err) {
    console.error('[App onError]', err)
  },

  onUnhandledRejection(res) {
    console.warn('[App unhandledRejection]', res && res.reason)
  }
}
</script>

<style lang="scss">
@import '@/styles/reset.scss';
@import '@/styles/common.scss';
/* #ifdef H5 */
@import '@/styles/pc.scss';
/* #endif */
</style>
