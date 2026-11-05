import { toast } from './ui'

/**
 * 三端统一扫码：小程序/App 用 uni.scanCode，H5 用 html5-qrcode
 * @returns {Promise<string>} 扫码结果
 */
export function scan(opts = {}) {
  const { onlyFromCamera = false, scanType = ['qrCode', 'barCode'] } = opts

  // #ifdef MP-WEIXIN || APP-PLUS
  return new Promise((resolve, reject) => {
    uni.scanCode({
      onlyFromCamera,
      scanType,
      success: (res) => resolve(res.result),
      fail: (err) => {
        if (/cancel/i.test(err.errMsg || '')) return reject({ cancel: true })
        toast('扫码失败，请重试')
        reject(err)
      }
    })
  })
  // #endif

  // #ifdef H5
  return scanByH5()
  // #endif
}

/* ---------------- H5 专用实现 ---------------- */
// scanner 与 stopH5Scan 必须三端都存在导出，避免小程序/App 编译时找不到符号
let scanner = null

// #ifdef H5

async function scanByH5() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    toast('当前环境不支持摄像头，请使用 https 或手动输入溯源码')
    return Promise.reject({ code: 'NO_CAMERA' })
  }

  const { Html5Qrcode } = await import('html5-qrcode')

  let el = document.getElementById('scan-reader')
  if (!el) {
    el = document.createElement('div')
    el.id = 'scan-reader'
    el.style.cssText = 'position:fixed;left:0;top:0;width:100%;height:100%;z-index:9999;background:#000;'
    document.body.appendChild(el)
  }

  scanner = new Html5Qrcode('scan-reader')

  return new Promise((resolve, reject) => {
    scanner
      .start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 240, height: 240 } },
        (text) => {
          stopH5Scan()
          resolve(text)
        },
        () => {}
      )
      .catch((err) => {
        stopH5Scan()
        toast('无法启动摄像头，请检查浏览器权限')
        reject(err)
      })
  })
}

// #endif

/** 停止 H5 扫码（非 H5 端为空实现，保证三端导入一致） */
export function stopH5Scan() {
  // #ifdef H5
  if (scanner) {
    const s = scanner
    scanner = null
    try {
      s.stop().then(() => s.clear()).catch(() => {})
    } catch (e) {
      /* ignore */
    }
  }
  const el = document.getElementById('scan-reader')
  if (el && el.parentNode) el.parentNode.removeChild(el)
  // #endif
}

/** 从扫码结果中提取溯源码：兼容纯编码与 URL 两种形态 */
export function extractTraceCode(raw) {
  if (!raw) return ''
  const s = String(raw).trim()
  // URL 形式：取 code 参数或最后一段
  const m = s.match(/[?&]code=([^&]+)/)
  if (m) return decodeURIComponent(m[1])
  if (/^https?:\/\//.test(s)) {
    const parts = s.replace(/\/+$/, '').split('/')
    return parts[parts.length - 1] || s
  }
  return s
}
