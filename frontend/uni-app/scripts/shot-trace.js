/**
 * 溯源页（移动端）可信度卡片截图
 * 用法：node scripts/shot-trace.js [溯源码]
 */
const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const CODE = process.argv[2] || 'AG20260512001'

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 390, height: 900, deviceScaleFactor: 2, isMobile: true, hasTouch: true })

  const errors = []
  page.on('pageerror', (e) => errors.push('PAGEERROR ' + e.message))
  page.on('console', (m) => m.type() === 'error' && errors.push('CONSOLE ' + m.text().slice(0, 200)))

  await page.goto(`${BASE}/pages/trace/index?code=${CODE}`, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1600))

  // 若页面未自动查询，手动输入并回车
  const hasTrust = await page.$('.trust')
  if (!hasTrust) {
    const input = await page.$('input')
    if (input) {
      await input.click()
      await page.keyboard.type(CODE, { delay: 30 })
      await page.keyboard.press('Enter')
      await new Promise((r) => setTimeout(r, 2000))
    }
  }

  await page.screenshot({ path: path.join(OUT, 'm-trace-trust.png') })
  await page.screenshot({ path: path.join(OUT, 'm-trace-trust-full.png'), fullPage: true })
  console.log('shot ok')
  console.log(errors.length ? errors.slice(0, 6).join('\n') : 'no console errors')
  await browser.close()
})()
