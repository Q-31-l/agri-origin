import { ENV } from '@/config/env'
import { getToken } from '@/utils/auth'

class SocketClient {
  constructor() {
    this.task = null
    this.connected = false
    this.listeners = new Map()
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.reconnectCount = 0
    this.manualClose = false
  }

  /**
   * 后端 context-path 为 /api，WebSocket 端点为 /api/ws?token=xxx。
   * 三端都用 ENV.baseURL 推导，不能简单替换主机名，否则会丢掉 /api 前缀。
   */
  get url() {
    const token = encodeURIComponent(getToken() || '')
    const base = ENV.baseURL
    // #ifdef H5
    // H5：开发环境 base 为相对路径 /api（走 vite 代理）；生产同域部署亦然
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    if (/^https?:/.test(base)) {
      return `${base.replace(/^http/, proto)}/ws?token=${token}`
    }
    return `${proto}://${location.host}${base}/ws?token=${token}`
    // #endif
    // #ifndef H5
    return `${base.replace(/^http/, 'ws')}/ws?token=${token}`
    // #endif
  }

  connect() {
    if (this.connected || this.task || !getToken()) return
    this.manualClose = false
    this.task = uni.connectSocket({ url: this.url, complete: () => {} })
    if (!this.task) return

    this.task.onOpen(() => {
      this.connected = true
      this.reconnectCount = 0
      this.startHeartbeat()
      this.emit('open')
    })

    this.task.onMessage((res) => {
      if (res.data === 'pong') return
      let msg
      try {
        msg = JSON.parse(res.data)
      } catch (e) {
        return
      }
      this.emit(msg.type || 'message', msg)
    })

    this.task.onClose(() => {
      this.connected = false
      this.task = null
      this.stopHeartbeat()
      this.emit('close')
      if (!this.manualClose) this.scheduleReconnect()
    })

    this.task.onError(() => {
      this.connected = false
      this.task = null
      this.stopHeartbeat()
      if (!this.manualClose) this.scheduleReconnect()
    })
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return
    const delay = Math.min(1000 * Math.pow(2, this.reconnectCount++), 30000)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.connected && this.task) this.task.send({ data: 'ping' })
    }, 25000)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  on(event, fn) {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set())
    this.listeners.get(event).add(fn)
    return () => this.off(event, fn)
  }

  off(event, fn) {
    const set = this.listeners.get(event)
    if (set) set.delete(fn)
  }

  emit(event, payload) {
    const set = this.listeners.get(event)
    if (set) set.forEach((fn) => {
      try {
        fn(payload)
      } catch (e) {
        console.error('[socket handler]', e)
      }
    })
  }

  close() {
    this.manualClose = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.task) {
      this.task.close({})
      this.task = null
    }
    this.connected = false
  }
}

export const socket = new SocketClient()
