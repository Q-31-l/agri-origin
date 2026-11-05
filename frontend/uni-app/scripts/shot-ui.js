/**
 * UI 细节巡检：侧栏玻璃态选中 + 品牌标识
 * 用法：node scripts/shot-ui.js
 */
const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

async function fillInput(page, index, text) {
  const el = (await page.$$('input'))[index]
  if (!el) return
  await el.click({ clickCount: 3 })
  await page.keyboard.down('Control')
  await page.keyboard.press('KeyA')
  await page.keyboard.up('Control')
  await page.keyboard.press('Backspace')
  await page.keyboard.type(text, { delay: 40 })
  await new Promise((r) => setTimeout(r, 200))
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })

  const errors = []
  page.on('pageerror', (e) => errors.push('PAGEERROR ' + e.message))
  page.on('console', (m) => m.type() === 'error' && errors.push('CONSOLE ' + m.text().slice(0, 200)))

  // 登录页
  await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1200))
  await page.screenshot({ path: path.join(OUT, 'ui-login.png') })

  await fillInput(page, 0, 'admin')
  await fillInput(page, 1, '123456')
  const btn = await page.evaluateHandle(() =>
    Array.from(document.querySelectorAll('.btn')).find((el) => /^登\s*录$/.test((el.textContent || '').trim()))
  )
  if (btn && btn.asElement()) await btn.asElement().click()
  await new Promise((r) => setTimeout(r, 2400))

  for (const [name, href] of [
    ['ui-dashboard', '/pages-pc/dashboard/index'],
    ['ui-abnormal', '/pages-pc/abnormal/index'],
    ['ui-batch-detail', '/pages-pc/batch/detail?id=1']
  ]) {
    await page.goto(BASE + href, { waitUntil: 'networkidle2' })
    await new Promise((r) => setTimeout(r, 1500))
    await page.screenshot({ path: path.join(OUT, name + '.png') })
    console.log('OK', name)
  }

  // 侧栏特写（放大 2 倍裁切，看毛玻璃细节）
  const side = await page.$('.side')
  if (side) await side.screenshot({ path: path.join(OUT, 'ui-side.png') })

  console.log(errors.length ? errors.slice(0, 8).join('\n') : 'no console errors')
  await browser.close()
})()
