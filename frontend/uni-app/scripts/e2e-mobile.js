const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const fs = require('fs')
const path = require('path')

const OUT = 'C:/Users/30786/Desktop/毕设农产品/docs/assets/screenshots/uni'
fs.mkdirSync(OUT, { recursive: true })
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'http://127.0.0.1:5173'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true })

  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', (e) => errors.push('PAGEERROR ' + e.message))

  // 登录
  await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1200)
  await page.screenshot({ path: path.join(OUT, 'm-login.png') })

  const inputs = await page.$$('input')
  if (inputs.length >= 2) {
    await inputs[0].click()
    await page.keyboard.type('admin', { delay: 30 })
    await inputs[1].click()
    await page.keyboard.type('123456', { delay: 30 })
  }
  const btns = await page.$$('.btn')
  if (btns.length) await btns[0].click()
  await sleep(2600)
  console.log('after login url:', page.url())
  await page.screenshot({ path: path.join(OUT, 'm-home.png') })

  // 批次列表
  await page.goto(BASE + '/pages/tabbar/batch', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1800)
  await page.screenshot({ path: path.join(OUT, 'm-batch-list.png') })

  // 批次详情
  await page.goto(BASE + '/pages/batch/detail?id=2', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2000)
  await page.screenshot({ path: path.join(OUT, 'm-batch-detail.png') })

  // 溯源
  await page.goto(BASE + '/pages/trace/index?code=TC20260512001', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2000)
  await page.screenshot({ path: path.join(OUT, 'm-trace.png'), fullPage: true })

  // 工作台（登录态下）
  await page.goto(BASE + '/pages/tabbar/home', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2000)
  await page.screenshot({ path: path.join(OUT, 'm-home.png') })

  // 我的
  await page.goto(BASE + '/pages/tabbar/mine', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)
  await page.screenshot({ path: path.join(OUT, 'm-mine.png') })

  console.log('errors:', errors.length ? errors.slice(0, 10).join('\n') : 'none')
  await browser.close()
})()
