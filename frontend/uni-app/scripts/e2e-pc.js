/**
 * PC 端（1440×900）截图与冒烟测试
 * 用法：node scripts/e2e-pc.js [--only=login,home,...]
 */
const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')

const CHROME =
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

function log(...a) {
  console.log('[pc]', ...a)
}

/**
 * 稳定填写 uni-app 的 input：点击聚焦 → 全选 → 逐字输入 → 校验回读。
 * 末位丢字是常见现象，校验失败时重试。
 */
async function fillInput(page, index, text, tries = 3) {
  for (let i = 0; i < tries; i++) {
    const inputs = await page.$$('input')
    const el = inputs[index]
    if (!el) return
    await el.click({ clickCount: 3 })
    await page.keyboard.down('Control')
    await page.keyboard.press('KeyA')
    await page.keyboard.up('Control')
    await page.keyboard.press('Backspace')
    await page.keyboard.type(text, { delay: 40 })
    await new Promise((r) => setTimeout(r, 260))
    const v = await page.evaluate((idx) => {
      const list = document.querySelectorAll('input')
      return list[idx] ? list[idx].value : ''
    }, index)
    if (v === text) return
    log(`fillInput retry ${i + 1}: got "${v}" want "${text}"`)
  }
}

async function main() {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })

  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--window-size=1440,900']
  })

  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })

  const errors = []
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message))
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push('CONSOLE: ' + m.text().slice(0, 300))
  })

  // ---------- 登录 ----------
  await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1200))

  const inputs = await page.$$('input')
  log('login inputs =', inputs.length)

  // 第一个输入框：用户名；第二个：密码（输入后校验，避免末位丢字）
  await fillInput(page, 0, 'admin')
  await fillInput(page, 1, '123456')

  const vals = await page.evaluate(() =>
    Array.from(document.querySelectorAll('input')).map((i) => i.value)
  )
  log('input values =', JSON.stringify(vals))

  const okBtn = await page.evaluateHandle(() => {
    // 登录按钮：class 含 btn，且文本恰好是「登 录」/「登录」
    const all = Array.from(document.querySelectorAll('.btn'))
    return all.find((el) => /^登\s*录$/.test((el.textContent || '').trim()))
  })
  if (okBtn && okBtn.asElement()) {
    await okBtn.asElement().click()
    log('clicked submit button')
  } else {
    log('submit button NOT found')
  }
  await new Promise((r) => setTimeout(r, 2200))
  log('after login url =', page.url())

  const token = await page.evaluate(() => localStorage.getItem('agri_token'))
  log('token =', token ? token.slice(0, 20) + '...' : 'MISSING')

  // ---------- PC 页面巡检 ----------
  const pages = [
    ['pc-dashboard', '/pages-pc/dashboard/index'],
    ['pc-batch-list', '/pages-pc/batch/list'],
    ['pc-planting', '/pages-pc/planting/index'],
    ['pc-harvest', '/pages-pc/harvest/index'],
    ['pc-quality', '/pages-pc/quality/index'],
    ['pc-logistics', '/pages-pc/logistics/index'],
    ['pc-sales', '/pages-pc/sales/index'],
    ['pc-abnormal', '/pages-pc/abnormal/index'],
    ['pc-chain', '/pages-pc/chain/index'],
    ['pc-trace', '/pages-pc/trace/index'],
    ['pc-sys-user', '/pages-pc/system/user'],
    ['pc-sys-farm', '/pages-pc/system/farm'],
    ['pc-sys-product', '/pages-pc/system/product'],
    ['pc-sys-log', '/pages-pc/system/log']
  ]

  const results = []
  for (const [name, href] of pages) {
    await page.goto(BASE + href, { waitUntil: 'networkidle2' })
    await new Promise((r) => setTimeout(r, 1400))
    const file = path.join(OUT, name + '.png')
    await page.screenshot({ path: file })
    const info = await page.evaluate(() => {
      const t = document.body.innerText.replace(/\s+/g, ' ').slice(0, 120)
      return { text: t, h: document.body.scrollHeight }
    })
    results.push({ name, ...info })
    log(name, '->', info.text.slice(0, 60))
  }

  // ---------- 批次详情（取真实 id）----------
  const batchId = await page.evaluate(async () => {
    // uni H5 存储结构：{"type":"object","data":{"v":..,"t":..,"e":0}}
    let raw = localStorage.getItem('agri_token')
    let token = raw
    try {
      let o = JSON.parse(raw)
      if (o && o.data && o.data.v) o = o.data
      token = o && o.v ? o.v : raw
    } catch (e) {
      /* 原样使用 */
    }
    const r = await fetch('/api/batch/page?page=1&size=1', {
      headers: { Authorization: 'Bearer ' + token }
    })
    const j = await r.json()
    return j && j.data && j.data.records && j.data.records[0] && j.data.records[0].id
  })
  if (batchId) {
    await page.goto(BASE + '/pages-pc/batch/detail?id=' + batchId, {
      waitUntil: 'networkidle2'
    })
    await new Promise((r) => setTimeout(r, 1800))
    await page.screenshot({ path: path.join(OUT, 'pc-batch-detail.png') })
    log('detail ok, id =', batchId)
  } else {
    log('detail skipped: no batch id')
  }

  fs.writeFileSync(
    path.join(OUT, 'report.json'),
    JSON.stringify({ results, errors, batchId }, null, 2)
  )

  log('errors =', errors.length)
  errors.slice(0, 12).forEach((e) => log('  !', e))

  await browser.close()
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
