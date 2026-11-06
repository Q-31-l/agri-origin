/**
 * H5 扫码取景层（原生 DOM 实现，仅 H5 调用）
 *
 * 为什么不用 .vue 组件：
 *   1. overlay 要 fixed 全屏并盖住 tabBar，走 uni-app 组件树容易被父级
 *      transform / overflow 裁剪；
 *   2. 小程序与 App 用 uni.scanCode 原生扫码，根本走不到这里，放进 .vue
 *      只会多出三份用不上的条件编译分支。
 *
 * 解码库为什么用静态 import：
 *   本文件只在 scan.js 的 H5 条件编译块里被 import()，本身就是一个独立
 *   chunk，写静态 import 仍保持"点扫码才加载"。反过来，如果在这里再写
 *   import('html5-qrcode')，uni-app 会按模块路径推导出
 *   "..-node_modules-html5-qrcode-esm-index.xxx.js" 这种以 .. 开头的 chunk 名，
 *   生成的引用少了 './' 前缀，浏览器按裸模块名解析直接抛
 *   Failed to resolve module specifier —— 扫码会永远起不来。
 *
 * 能力边界：
 *   - 安全上下文（HTTPS / localhost）且存在摄像头 → 实时取景解码
 *   - 非安全上下文、无摄像头、权限被拒 → 拍照识别 / 相册识别（不需摄像头）
 *   两者最终都返回二维码文本，由调用方 extractTraceCode 归一。
 */
import { Html5Qrcode } from 'html5-qrcode'

const STYLE_ID = 'agri-scan-style'
const ROOT_ID = 'agri-scan-root'
const READER_ID = 'agri-scan-reader'

/* ---------------- DOM 样式 ---------------- */

const CSS = `
#${ROOT_ID}{
  position:fixed;top:0;right:0;bottom:0;left:0;z-index:99999;
  background:#000;display:flex;flex-direction:column;color:#fff;
  font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Helvetica Neue",Arial,sans-serif;
  -webkit-tap-highlight-color:transparent;
}
#${ROOT_ID} *{box-sizing:border-box;-webkit-user-select:none;user-select:none;}
.asc-bar{
  flex:0 0 auto;display:flex;align-items:center;justify-content:space-between;
  padding:calc(env(safe-area-inset-top, 0px) + 12px) 16px 10px;position:relative;z-index:4;
}
.asc-circle{
  width:38px;height:38px;border-radius:50%;background:rgba(255,255,255,.15);
  display:flex;align-items:center;justify-content:center;font-size:17px;line-height:1;
  cursor:pointer;transition:background .15s;border:none;color:#fff;padding:0;
}
.asc-circle:active{background:rgba(255,255,255,.32);}
.asc-circle[disabled]{opacity:.35;pointer-events:none;}
.asc-title{font-size:16px;font-weight:600;letter-spacing:.4px;}
.asc-stage{flex:1 1 auto;position:relative;overflow:hidden;background:#000;}
#${READER_ID}{position:absolute;top:0;left:0;width:100%;height:100%;}
#${READER_ID} video{width:100%!important;height:100%!important;object-fit:cover;display:block;}
#${READER_ID} canvas{display:none!important;}

/* 取景框：纯视觉引导，解码走全画面，避免"框内没反应"的错觉 */
.asc-frame{
  position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);
  width:min(68vw,300px);height:min(68vw,300px);pointer-events:none;z-index:2;
}
.asc-corner{position:absolute;width:26px;height:26px;border:3px solid #4ade80;border-radius:3px;}
.asc-corner.tl{top:0;left:0;border-right:none;border-bottom:none;border-top-left-radius:10px;}
.asc-corner.tr{top:0;right:0;border-left:none;border-bottom:none;border-top-right-radius:10px;}
.asc-corner.bl{bottom:0;left:0;border-right:none;border-top:none;border-bottom-left-radius:10px;}
.asc-corner.br{bottom:0;right:0;border-left:none;border-top:none;border-bottom-right-radius:10px;}
.asc-sweep{
  position:absolute;left:8px;right:8px;height:2px;border-radius:2px;
  background:linear-gradient(90deg,rgba(74,222,128,0),#4ade80,rgba(74,222,128,0));
  animation:asc-sweep 2.2s ease-in-out infinite;
}
@keyframes asc-sweep{0%{top:5%;opacity:0}12%{opacity:1}88%{opacity:1}100%{top:95%;opacity:0}}

.asc-hint{
  position:absolute;left:0;right:0;bottom:calc(env(safe-area-inset-bottom, 0px) + 14px);
  text-align:center;font-size:13px;line-height:1.6;color:rgba(255,255,255,.72);
  padding:0 28px;z-index:2;pointer-events:none;
}

.asc-actions{
  flex:0 0 auto;display:flex;gap:12px;justify-content:center;align-items:center;
  padding:14px 20px calc(env(safe-area-inset-bottom, 0px) + 20px);
  background:rgba(0,0,0,.92);position:relative;z-index:4;
}
.asc-act{
  flex:1 1 0;max-width:190px;height:46px;border-radius:23px;border:1px solid rgba(255,255,255,.22);
  background:rgba(255,255,255,.10);color:#fff;font-size:14px;
  display:flex;align-items:center;justify-content:center;gap:7px;cursor:pointer;
}
.asc-act:active{background:rgba(255,255,255,.22);}
.asc-act .ico{font-size:16px;line-height:1;}

/* 错误 / 提示态 */
.asc-state{
  position:absolute;top:0;left:0;width:100%;height:100%;z-index:5;
  display:flex;flex-direction:column;align-items:center;justify-content:center;
  padding:32px 28px calc(env(safe-area-inset-bottom, 0px) + 32px);text-align:center;
  background:#0b0f14;
}
.asc-state-ico{font-size:40px;line-height:1;margin-bottom:16px;}
.asc-state-title{font-size:16px;font-weight:600;margin-bottom:10px;}
.asc-state-desc{font-size:13px;line-height:1.75;color:rgba(255,255,255,.62);max-width:340px;}
.asc-state-desc b{color:#fbbf24;font-weight:600;}
.asc-state-btns{display:flex;flex-direction:column;gap:10px;margin-top:22px;width:100%;max-width:280px;}
.asc-solid{
  height:44px;border-radius:22px;border:none;background:#3b7cf6;color:#fff;font-size:14px;
  cursor:pointer;display:flex;align-items:center;justify-content:center;gap:6px;
}
.asc-solid:active{background:#2f66d0;}
.asc-ghost{
  height:44px;border-radius:22px;border:1px solid rgba(255,255,255,.24);background:transparent;
  color:rgba(255,255,255,.86);font-size:14px;cursor:pointer;
  display:flex;align-items:center;justify-content:center;gap:6px;
}
.asc-toggle{
  margin-top:18px;font-size:12.5px;color:#7fb0ff;text-decoration:underline;
  background:none;border:none;cursor:pointer;padding:6px;
}

.asc-toast{
  position:absolute;left:50%;bottom:110px;transform:translateX(-50%);
  background:rgba(0,0,0,.82);color:#fff;font-size:13px;padding:10px 18px;border-radius:20px;
  z-index:9;white-space:nowrap;max-width:86vw;overflow:hidden;text-overflow:ellipsis;
}

.asc-loading{
  position:absolute;top:0;left:0;width:100%;height:100%;z-index:6;
  display:flex;flex-direction:column;align-items:center;justify-content:center;
  background:rgba(0,0,0,.72);gap:14px;
}
.asc-spinner{
  width:34px;height:34px;border:3px solid rgba(255,255,255,.2);border-top-color:#4ade80;
  border-radius:50%;animation:asc-spin .8s linear infinite;
}
@keyframes asc-spin{to{transform:rotate(360deg)}}
.asc-loading-text{font-size:13px;color:rgba(255,255,255,.8);}
`

/* ---------------- 工具 ---------------- */

/** 当前是否处于浏览器允许调用摄像头的安全上下文 */
export function isSecureOrigin() {
  if (typeof window === 'undefined') return false
  if (window.isSecureContext) return true
  const h = location.hostname
  return (
    location.protocol === 'https:' ||
    h === 'localhost' ||
    h === '127.0.0.1' ||
    h === '::1' ||
    h === '[::1]'
  )
}

/**
 * 推导同站点的 HTTPS 地址
 * 裸 IP 没有证书，用 sslip.io 的通配解析（8.130.105.150 → 8-130-105-150.sslip.io）；
 * 已经是域名时只换协议，不擅改主机名。
 */
export function toHttpsUrl() {
  const h = location.hostname
  const tail = location.pathname + location.search + location.hash
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(h)) {
    return `https://${h.replace(/\./g, '-')}.sslip.io${tail}`
  }
  return `https://${h}${tail}`
}

/** 实时取景是否可用（安全上下文 + 有摄像头 API） */
export function canUseLiveCamera() {
  return (
    isSecureOrigin() &&
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function'
  )
}

/** 是否存在可用摄像头（异步探测，失败一律视为没有） */
export async function probeCamera() {
  if (!canUseLiveCamera()) return false
  try {
    const list = await navigator.mediaDevices.enumerateDevices()
    return list.some((d) => d.kind === 'videoinput')
  } catch (e) {
    return false
  }
}

/** 把 getUserMedia 的异常翻译成用户能照着做的说明 */
function explainCameraError(err) {
  const name = (err && (err.name || err.code)) || ''
  const raw = String((err && err.message) || err || '')

  if (name === 'NotAllowedError' || name === 'PermissionDeniedError' || /permission|denied/i.test(raw)) {
    return {
      title: '摄像头权限被拒绝',
      desc:
        '浏览器记住了上次的"拒绝"。请点击地址栏左侧的锁形图标 → 将"摄像头"改为"允许" → 刷新页面。' +
        '也可以直接用下方的拍照识别，无需摄像头权限。'
    }
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return {
      title: '未检测到摄像头',
      desc: '这台设备没有可用的摄像头。请改用拍照识别或相册选图，同样能读取二维码。'
    }
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return {
      title: '摄像头被占用',
      desc: '摄像头正被其他程序使用（视频会议、相机等）。请关闭后重试，或改用拍照识别。'
    }
  }
  if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
    return {
      title: '摄像头不支持所需参数',
      desc: '已尝试切换其它摄像头仍失败。请改用拍照识别，或换一台设备。'
    }
  }
  if (name === 'SecurityError' || !isSecureOrigin()) {
    return {
      title: '当前地址不允许调用摄像头',
      desc:
        '浏览器规定：只有 <b>HTTPS</b> 或 localhost 页面才能打开摄像头。' +
        '请改用下方的拍照识别（仍会调起手机相机），或访问站点的 HTTPS 地址。'
    }
  }
  return {
    title: '无法启动摄像头',
    desc: '可以重试一次，或直接使用拍照识别 / 从相册选择。'
  }
}

function ensureStyle() {
  if (document.getElementById(STYLE_ID)) return
  const s = document.createElement('style')
  s.id = STYLE_ID
  s.textContent = CSS
  document.head.appendChild(s)
}

function el(tag, cls, text) {
  const n = document.createElement(tag)
  if (cls) n.className = cls
  if (text != null) n.textContent = text
  return n
}

function showToast(root, msg, ms = 2200) {
  const old = root.querySelector('.asc-toast')
  if (old) old.remove()
  const t = el('div', 'asc-toast', msg)
  root.appendChild(t)
  setTimeout(() => t.remove(), ms)
}

/** 解码库已随本 chunk 一起加载，保留函数签名方便调用处统一处理 */
async function loadLib() {
  if (!Html5Qrcode) throw new Error('扫码组件加载失败')
  return Html5Qrcode
}

/**
 * 打开扫码层
 * @param {{title?:string}} opts
 * @returns {Promise<{code:string}|{manual:true}|{cancel:true}>}
 */
export function openScanner(opts = {}) {
  if (typeof document === 'undefined') {
    return Promise.reject({ code: 'NO_DOM' })
  }
  ensureStyle()

  return new Promise((resolve) => {
    const root = el('div')
    root.id = ROOT_ID
    root.setAttribute('role', 'dialog')
    root.setAttribute('aria-modal', 'true')

    /* ---- 顶栏 ---- */
    const bar = el('div', 'asc-bar')
    const closeBtn = el('button', 'asc-circle', '✕')
    closeBtn.setAttribute('aria-label', '关闭扫码')
    const title = el('div', 'asc-title', opts.title || '扫描溯源码')
    const torchBtn = el('button', 'asc-circle', '☀')
    torchBtn.setAttribute('aria-label', '手电筒')
    torchBtn.setAttribute('disabled', '')
    bar.append(closeBtn, title, torchBtn)

    /* ---- 取景区 ---- */
    const stage = el('div', 'asc-stage')
    const reader = el('div')
    reader.id = READER_ID
    const frame = el('div', 'asc-frame')
    frame.innerHTML =
      '<div class="asc-corner tl"></div><div class="asc-corner tr"></div>' +
      '<div class="asc-corner bl"></div><div class="asc-corner br"></div>' +
      '<div class="asc-sweep"></div>'
    const hint = el('div', 'asc-hint', '把包装上的二维码放进取景框，识别成功会自动跳转')
    stage.append(reader, frame, hint)

    /* ---- 底部操作 ---- */
    const actions = el('div', 'asc-actions')
    const shootBtn = el('button', 'asc-act')
    shootBtn.innerHTML = '<span class="ico">📷</span>拍照识别'
    const albumBtn = el('button', 'asc-act')
    albumBtn.innerHTML = '<span class="ico">🖼</span>从相册选择'
    actions.append(shootBtn, albumBtn)

    root.append(bar, stage, actions)
    document.body.appendChild(root)
    document.body.style.overflow = 'hidden'

    let scanner = null
    let closed = false
    let decoding = false

    function cleanup() {
      if (closed) return
      closed = true
      document.body.style.overflow = ''
      try {
        if (scanner) {
          const s = scanner
          scanner = null
          s.stop()
            .catch(() => {})
            .then(() => {
              try {
                s.clear()
              } catch (e) {
                /* ignore */
              }
            })
        }
      } catch (e) {
        /* ignore */
      }
      setTimeout(() => {
        if (root.parentNode) root.parentNode.removeChild(root)
      }, 0)
    }

    function finish(result) {
      cleanup()
      resolve(result)
    }

    closeBtn.addEventListener('click', () => finish({ cancel: true }))

    /* ---- 文件识别（拍照 / 相册），不依赖摄像头 ---- */
    function pickImage(capture) {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = 'image/*'
      if (capture) input.setAttribute('capture', 'environment')
      input.style.display = 'none'
      document.body.appendChild(input)
      input.addEventListener('change', async () => {
        const file = input.files && input.files[0]
        setTimeout(() => input.remove(), 0)
        if (!file) return
        if (file.size > 12 * 1024 * 1024) {
          showToast(root, '图片过大，请压缩后再试')
          return
        }
        decoding = true
        showLoading('正在识别二维码…')
        try {
          const Ctor = await loadLib()
          const tmp = el('div')
          tmp.id = 'asc-file-reader'
          tmp.style.display = 'none'
          document.body.appendChild(tmp)
          const s = new Ctor('asc-file-reader')
          const text = await s.scanFile(file, false)
          tmp.remove()
          if (text) {
            stopLive()
            finish({ code: String(text) })
          } else {
            hideLoading()
            showToast(root, '图片里没有识别到二维码')
          }
        } catch (e) {
          hideLoading()
          console.error('[scan] 图片解码失败:', e && (e.name || e.code), e && e.message, e)
          showToast(root, isSecureOrigin() ? '未识别到二维码，换一张试试' : '识别失败，请换一张清晰的二维码图片')
        } finally {
          decoding = false
        }
      })
      input.click()
    }

    shootBtn.addEventListener('click', () => pickImage(true))
    albumBtn.addEventListener('click', () => pickImage(false))

    /* ---- 加载 / 状态层 ---- */
    let loadingEl = null
    function showLoading(text) {
      hideLoading()
      loadingEl = el('div', 'asc-loading')
      loadingEl.innerHTML = '<div class="asc-spinner"></div>'
      loadingEl.appendChild(el('div', 'asc-loading-text', text || '加载中…'))
      root.appendChild(loadingEl)
    }
    function hideLoading() {
      if (loadingEl) {
        loadingEl.remove()
        loadingEl = null
      }
    }

    /** 摄像头不可用时，把取景区换成说明 + 可操作按钮 */
    function showCameraError(err) {
      hideLoading()
      frame.style.display = 'none'
      hint.style.display = 'none'
      const info = explainCameraError(err)

      const box = el('div', 'asc-state')
      box.innerHTML = `
        <div class="asc-state-ico">⚠</div>
        <div class="asc-state-title">${info.title}</div>
        <div class="asc-state-desc">${info.desc}</div>
      `
      const btns = el('div', 'asc-state-btns')
      const b1 = el('button', 'asc-solid')
      b1.innerHTML = '<span>📷</span>拍照识别'
      b1.addEventListener('click', () => pickImage(true))
      const b2 = el('button', 'asc-ghost')
      b2.innerHTML = '<span>🖼</span>从相册选择'
      b2.addEventListener('click', () => pickImage(false))
      const b3 = el('button', 'asc-ghost', '手动输入溯源码')
      b3.addEventListener('click', () => finish({ manual: true }))
      btns.append(b1, b2, b3)

      if (isSecureOrigin()) {
        const retry = el('button', 'asc-toggle', '重试摄像头')
        retry.addEventListener('click', () => {
          box.remove()
          frame.style.display = ''
          hint.style.display = ''
          startLive()
        })
        box.append(btns, retry)
      } else {
        const tip = el('button', 'asc-toggle', '想用实时扫码？切换 HTTPS 地址')
        tip.addEventListener('click', () => {
          location.href = toHttpsUrl()
        })
        box.append(btns, tip)
      }
      stage.appendChild(box)
    }

    /* ---- 实时取景 ---- */
    async function startLive() {
      if (closed) return
      if (!canUseLiveCamera()) {
        showCameraError({ name: 'SecurityError' })
        return
      }
      showLoading('正在启动摄像头…')
      try {
        const Ctor = await loadLib()
        scanner = new Ctor(READER_ID, { verbose: false })
        const conf = { fps: 10, aspectRatio: 1.0 }
        try {
          await scanner.start(
            { facingMode: 'environment' },
            conf,
            (text) => {
              if (decoding || closed) return
              decoding = true
              stopLive()
              finish({ code: String(text) })
            },
            () => {}
          )
        } catch (e1) {
          // 后置摄像头不可用（PC / 单摄设备）→ 退回任意可用摄像头
          let list = []
          try {
            list = await Ctor.getCameras()
          } catch (e) {
            /* ignore */
          }
          if (!list.length) throw e1
          await scanner.start(
            list[0].id,
            conf,
            (text) => {
              if (decoding || closed) return
              decoding = true
              stopLive()
              finish({ code: String(text) })
            },
            () => {}
          )
        }
        hideLoading()
        enableTorch()
      } catch (e) {
        console.error('[scan] 摄像头启动失败:', e && (e.name || e.code), e && e.message, e)
        showCameraError(e)
      }
    }

    function stopLive() {
      if (!scanner) return
      const s = scanner
      scanner = null
      try {
        s.stop()
          .catch(() => {})
          .then(() => {
            try {
              s.clear()
            } catch (e) {
              /* ignore */
            }
          })
      } catch (e) {
        /* ignore */
      }
    }

    /** 有闪光灯的设备才启用按钮 */
    async function enableTorch() {
      try {
        await scanner.applyVideoConstraints({ advanced: [{ torch: true }] })
        torchBtn.removeAttribute('disabled')
        let on = false
        torchBtn.addEventListener('click', async () => {
          on = !on
          try {
            await scanner.applyVideoConstraints({ advanced: [{ torch: on }] })
            torchBtn.style.background = on ? 'rgba(251,191,36,.9)' : ''
            torchBtn.style.color = on ? '#111' : ''
          } catch (e) {
            showToast(root, '该设备不支持手电筒')
          }
        })
      } catch (e) {
        // 不支持 torch 的设备（iOS Safari 等）保持禁用
      }
    }

    // 用户直接拒绝过权限时不要反复弹窗，先探测一次
    startLive()
  })
}
