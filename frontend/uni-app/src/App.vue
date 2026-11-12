<script>
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { useCacheStore } from '@/store/cache'
import { socket } from '@/utils/socket'
import { getToken, isTokenExpiringSoon } from '@/utils/auth'
import { refreshAccessToken } from '@/utils/token'

/**
 * 冷启动 / 回前台前先确认本地令牌还能用。
 *
 * access token 只有 12 小时（application.yml 的 jwt.expire-hours），
 * 隔天再打开时本地那把已经过期：直接拿去请求，第一条 /auth/profile 必然 401，
 * 紧接着 WebSocket 握手也会被服务端 401 挡掉，于是控制台一进来就是两条红字。
 * 这两条红字其实是"过期 → 静默续期 → 成功"过程中的正常中间态，
 * 但看起来很像小程序坏了，所以改成先续期再动：
 * 令牌还新鲜时不发任何额外请求，与原来的行为完全一致。
 */
function ensureFreshToken() {
  if (!getToken()) return Promise.resolve(false)
  return isTokenExpiringSoon() ? refreshAccessToken() : Promise.resolve(true)
}

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

    // 请求层 401 时清空内存态
    uni.$on('auth:expired', () => {
      useUserStore().reset()
      useCacheStore().reset()
      socket.close()
    })

    // 静默续期成功：把新 token / 用户信息 / 权限同步进内存态
    uni.$on('auth:refreshed', (data) => useUserStore().applyRefreshed(data))

    bindRealtime()

    // 冷启动静默刷新用户信息，保证多端数据一致（同样先确认令牌可用）
    if (user.token) {
      ensureFreshToken().then((ok) => {
        if (!ok) return
        const u = useUserStore()
        if (u.token) u.restore()
      })
    }

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
    // 令牌还新鲜就直接连；已经过期/临期就先续期再连，
    // 否则握手会被服务端 401 拒掉，控制台里就是一条红色的 WebSocket connection failed
    ensureFreshToken().then((ok) => {
      if (ok) socket.connect()
    })
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
