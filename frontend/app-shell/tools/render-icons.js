/**
 * 用项目既有品牌 SVG（static/logo/mark.svg）渲染 Android 图标与启动图。
 * 输出：res 下各 mipmap 密度的 ic_launcher / ic_launcher_round / ic_launcher_foreground，
 *      以及 res/drawable-xxhdpi/splash_logo.png
 */
const fs = require('fs')
const path = require('path')
const puppeteer = require(process.env.SHOTENV_PUPPETEER ||
  'C:/Users/30786/Desktop/毕设农产品/.workbuddy/shotenv/node_modules/puppeteer-core')

const EDGE = process.env.EDGE_BIN || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const ROOT = 'C:/Users/30786/Desktop/毕设农产品'
const SVG_RAW = fs.readFileSync(path.join(ROOT, 'frontend/uni-app/src/static/logo/mark.svg'), 'utf8')
// 根 svg 自带 width/height=512 的固有尺寸，会撑破目标画布，改为百分比铺满
const SVG = SVG_RAW.replace('width="512" height="512"', 'width="100%" height="100%"')
const OUT = path.join(ROOT, 'frontend/app-shell/res')

const DENSITY = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 }

function page(w, h, inner, radius) {
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><style>
  html,body{margin:0;padding:0;background:transparent;width:${w}px;height:${h}px;overflow:hidden}
  .wrap{width:${w}px;height:${h}px;${radius ? `border-radius:${radius};overflow:hidden;` : ''}
  .wrap svg{display:block;width:100%;height:100%}
  .center{width:${w}px;height:${h}px;display:flex;align-items:center;justify-content:center}
  .inner{width:${Math.round(w * 0.64)}px;height:${Math.round(h * 0.64)}px}
  .inner svg{display:block;width:100%;height:100%}
  </style></head><body>${inner}</body></html>`
}

async function shot(browser, w, h, inner, file, opts = {}) {
  const p = await browser.newPage()
  await p.setViewport({ width: w, height: h, deviceScaleFactor: 1 })
  await p.setContent(page(w, h, inner, opts.radius), { waitUntil: 'load' })
  await p.screenshot({ path: file, omitBackground: !!opts.transparent })
  await p.close()
  console.log('ok', path.basename(file), w + 'x' + h)
}

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--allow-file-access-from-files']
  })

  const full = `<div class="wrap">${SVG}</div>`
  const round = `<div class="wrap" style="border-radius:50%">${SVG}</div>`
  const fg = `<div class="center"><div class="inner">${SVG}</div></div>`

  for (const [name, scale] of Object.entries(DENSITY)) {
    const dir = path.join(OUT, 'mipmap-' + name)
    fs.mkdirSync(dir, { recursive: true })
    const size = Math.round(48 * scale)
    await shot(browser, size, size, full, path.join(dir, 'ic_launcher.png'))
    await shot(browser, size, size, round, path.join(dir, 'ic_launcher_round.png'))
  }

  // 自适应图标前景：108dp 画布，图形占 64%（安全区内）
  for (const name of ['xxhdpi', 'xxxhdpi']) {
    const dir = path.join(OUT, 'mipmap-' + name)
    const size = Math.round(108 * DENSITY[name])
    await shot(browser, size, size, fg, path.join(dir, 'ic_launcher_foreground.png'),
      { transparent: true })
  }

  // 启动图：xxhdpi 384px = 128dp
  const drawable = path.join(OUT, 'drawable-xxhdpi')
  fs.mkdirSync(drawable, { recursive: true })
  await shot(browser, 384, 384, full, path.join(drawable, 'splash_logo.png'))

  await browser.close()
})().catch(e => { console.error(e); process.exit(1) })
