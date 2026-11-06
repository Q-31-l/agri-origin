/**
 * 三端统一扫码入口
 *
 *   小程序 / App : uni.scanCode（原生相机，权限由宿主 App 申请，自带相册入口）
 *   H5           : 自建取景层（utils/scan-overlay）
 *                  · HTTPS/localhost → 实时摄像头解码
 *                  · HTTP、无摄像头、权限被拒 → 拍照识别 / 相册识图
 *
 * 注意：浏览器只在**安全上下文**（HTTPS 或 localhost）下开放
 * navigator.mediaDevices。纯 HTTP + 公网 IP 访问时，实时取景一定失败，
 * 这不是代码 bug，是浏览器的硬性规定——所以 H5 必须保留不依赖摄像头的
 * 图片识别通道，否则用户就只剩手动输入一条路。
 */
import { toast } from './ui'

const SCAN_TYPES = ['qrCode', 'barCode']

/**
 * 扫码
 * @param {{onlyFromCamera?: boolean, title?: string}} opts
 * @returns {Promise<string|null>} 二维码文本；用户取消或选择手动输入时返回 null
 */
export function scan(opts = {}) {
  const { onlyFromCamera = false, title = '扫描溯源码' } = opts

  // #ifdef MP-WEIXIN || APP-PLUS
  return new Promise((resolve, reject) => {
    uni.scanCode({
      onlyFromCamera,
      scanType: SCAN_TYPES,
      success: (res) => resolve(res.result || ''),
      fail: (err) => {
        const msg = err.errMsg || ''
        // 用户主动取消不算错误，返回 null 让调用方静默处理
        if (/cancel/i.test(msg)) return resolve(null)
        if (/auth|deny|permission/i.test(msg)) {
          toast('未获得相机权限，请到系统设置里开启')
          return resolve(null)
        }
        toast('扫码失败，请重试')
        reject(err)
      }
    })
  })
  // #endif

  // #ifdef H5
  return scanH5({ title })
  // #endif
}

/* ---------------- H5 ---------------- */

// #ifdef H5
let openScannerRef = null

async function scanH5({ title }) {
  try {
    if (!openScannerRef) {
      const mod = await import('./scan-overlay')
      openScannerRef = mod.openScanner
    }
    const res = await openScannerRef({ title })
    if (!res) return null
    if (res.cancel) return null
    if (res.manual) {
      // 交给页面：把焦点打到手动输入框，比再弹一次 toast 有用
      return null
    }
    return res.code || ''
  } catch (e) {
    if (e && e.cancel) return null
    toast('扫码组件加载失败，请刷新页面重试')
    return null
  }
}
// #endif

/**
 * 停止 H5 取景（保留导出，三端符号一致，页面 onUnload 直接调用）
 * 取景层自己会在关闭时释放摄像头，这里只兜底清理残留节点。
 */
export function stopH5Scan() {
  // #ifdef H5
  const root = document.getElementById('agri-scan-root')
  if (root && root.parentNode) {
    root.parentNode.removeChild(root)
    document.body.style.overflow = ''
  }
  // #endif
}

/** 判断实时取景是否可用（H5 用来决定按钮文案与引导） */
export function canScanLive() {
  // #ifdef H5
  if (typeof window === 'undefined') return false
  return (
    (window.isSecureContext || location.protocol === 'https:') &&
    !!navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function'
  )
  // #endif
  // #ifndef H5
  return true
  // #endif
}

/**
 * 从扫码结果中提取溯源码
 * 兼容：纯编码 / 带 code 参数的 URL / 路径末段 / 小程序 scene 参数
 */
export function extractTraceCode(raw) {
  if (raw == null) return ''
  let s = String(raw).trim()
  if (!s) return ''

  // 小程序码 scene 是 URL 编码的 kv，如 "code%3DTC2026..."
  if (/^[^/]*%3D/i.test(s) || /^code%3D/i.test(s)) {
    try {
      s = decodeURIComponent(s)
    } catch (e) {
      /* ignore */
    }
  }

  // URL 形态：优先取 code 参数
  const m = s.match(/[?&](?:code|traceCode|trace_code)=([^&#]+)/i)
  if (m) {
    try {
      return decodeURIComponent(m[1]).trim()
    } catch (e) {
      return m[1].trim()
    }
  }

  // 纯 URL：取路径最后一段（排除空段与 index）
  if (/^https?:\/\//i.test(s)) {
    const parts = s.replace(/[?#].*$/, '').replace(/\/+$/, '').split('/')
    const last = parts[parts.length - 1] || ''
    return decodeURIComponent(last).trim()
  }

  // 去掉可能被一起扫进来的空白与引号
  return s.replace(/^["'\s]+|["'\s]+$/g, '')
}
