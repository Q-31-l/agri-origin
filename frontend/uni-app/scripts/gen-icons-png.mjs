/**
 * 把 src/config/icons.js 的矢量图标导出为 PNG（base64），供移动端使用。
 *
 * 为什么移动端不能直接用 SVG：微信小程序的模板不支持 <svg> 标签，
 * 同一份页面要同时跑 H5 / 小程序 / App，只能用 <image> 加载 PNG。
 *
 * 用法（项目根目录）：
 *   NODE_PATH=<playwright 所在 node_modules> node scripts/gen-icons-png.mjs
 * 产物：src/config/icons-png.js
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright')

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const ICON_SRC = path.join(ROOT, 'src/config/icons.js')
const OUT = path.join(ROOT, 'src/config/icons-png.js')

/** 画布尺寸：56px 对应显示尺寸 ~22px，2.5 倍图足够清晰又不臃肿 */
const SIZE = 56
/** 移动端图标线宽略粗，小尺寸下不至于发虚 */
const STROKE = 1.95

/** 需要导出的「图标 + 颜色」组合 */
const JOBS = [
  // 工作台快捷操作：各自语义色（与原来的背景色块配套）
  ['plus', '#1f7a4d'],
  ['planting', '#2563eb'],
  ['harvest', '#b45309'],
  ['quality', '#7c3aed'],
  ['logistics', '#0891b2'],
  ['sales', '#c2410c'],
  ['abnormal', '#d93026'],
  ['scan', '#3b82f6'],
  // 「我的」等列表项：统一墨色
  ['batch', '#374151'],
  ['chain', '#374151'],
  ['lock', '#374151'],
  ['settings', '#374151'],
  ['image', '#374151'],
  ['refresh', '#374151'],
  // 备用：详情页 / 空态 / 表单
  ['trace', '#1f7a4d'],
  ['user', '#374151'],
  ['farm', '#374151'],
  ['product', '#374151'],
  ['log', '#374151'],
  ['location', '#1f7a4d'],
  ['camera', '#6b7280'],
  ['video', '#6b7280'],
  ['upload', '#1f7a4d'],
  ['clock', '#9ca3af'],
  ['check', '#16a34a'],
  ['close', '#6b7280'],
  ['search', '#9ca3af'],
  ['bell', '#374151'],
  ['arrowRight', '#1f7a4d'],
  ['chevronRight', '#9ca3af'],
  ['empty', '#c7cdd6'],
  // 登录页微信一键登录
  ['wechat', '#07c160'],
  ['scan', '#1f7a4d'],
  ['scan', '#ffffff']
]

function loadIcons() {
  let code = fs.readFileSync(ICON_SRC, 'utf8')
  code = code.replace('export const ICONS =', 'const ICONS =')
  code = code.replace(/export function iconMarkup[\s\S]*$/, '')
  const mod = { exports: {} }
  // eslint-disable-next-line no-new-func
  new Function('module', 'exports', code + '\n;module.exports = ICONS')(mod, mod.exports)
  return mod.exports
}

const ICONS = loadIcons()

const browser = await chromium.launch({ channel: 'msedge', headless: true })
const page = await browser.newPage()

const result = {}
let total = 0

for (const [name, color] of JOBS) {
  const markup = ICONS[name]
  if (!markup) {
    console.warn('  [跳过] 未定义图标:', name)
    continue
  }
  const dataUrl = await page.evaluate(
    ({ markup, color, SIZE, STROKE }) => {
      const svg =
        `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${SIZE}" height="${SIZE}"` +
        ` fill="none" stroke="${color}" stroke-width="${STROKE}" stroke-linecap="round"` +
        ` stroke-linejoin="round" color="${color}">${markup}</svg>`
      return new Promise((resolve, reject) => {
        const img = new Image()
        img.onload = () => {
          const c = document.createElement('canvas')
          c.width = SIZE
          c.height = SIZE
          const g = c.getContext('2d')
          g.drawImage(img, 0, 0, SIZE, SIZE)
          resolve(c.toDataURL('image/png'))
        }
        img.onerror = () => reject(new Error('render fail: ' + color))
        img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)))
      })
    },
    { markup, color, SIZE, STROKE }
  )

  if (!result[name]) result[name] = {}
  result[name][color] = dataUrl
  total += dataUrl.length
}

await browser.close()

const head = `/**
 * 移动端 PNG 图标（base64）—— 由 scripts/gen-icons-png.mjs 自动生成，请勿手改。
 *
 * 键是图标名，值是「颜色 → data URI」。字形源与 PC 端共用 src/config/icons.js，
 * 所以两端图标风格完全一致，只是移动端为了兼容小程序把矢量转成了位图。
 *
 * 改图标：改 icons.js 后重新执行生成脚本。
 */
export const PNG_ICONS = {
`

const body = Object.keys(result)
  .sort()
  .map((name) => {
    const colors = Object.entries(result[name])
      .map(([c, uri]) => `    ${JSON.stringify(c)}: ${JSON.stringify(uri)}`)
      .join(',\n')
    return `  ${JSON.stringify(name)}: {\n${colors}\n  }`
  })
  .join(',\n')

const tail = `
}

/**
 * 取图标 data URI。
 * @param {string} name 图标名（同 icons.js）
 * @param {string} color 颜色；未生成该色时退回最近似的已生成色，避免空白
 */
export function pngIcon(name, color = '#374151') {
  const set = PNG_ICONS[name]
  if (!set) return ''
  if (set[color]) return set[color]
  const keys = Object.keys(set)
  return keys.length ? set[keys[0]] : ''
}
`

fs.writeFileSync(OUT, head + body + tail, 'utf8')

console.log(`[OK] 生成 ${Object.keys(result).length} 个图标，共 ${(total / 1024).toFixed(1)} KB → ${path.relative(ROOT, OUT)}`)
