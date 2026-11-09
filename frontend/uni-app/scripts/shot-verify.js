/**
 * 临时校验脚本：登录后截取指定批次详情页，确认"外层状态"与"审核进度"显示一致。
 * 用法：node scripts/shot-verify.js <batchId> <outName>
 */
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const path = require('path')

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'https://8-130-105-150.sslip.io'
const OUT = 'C:/Users/30786/Desktop/毕设农产品/docs/assets/screenshots/uni'
const bid = process.argv[2] || '91'
const outName = process.argv[3] || 'verify-batch-detail.png'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    ignoreHTTPSErrors: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--ignore-certificate-errors']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true })

  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('pageerror', (e) => errors.push('PAGEERROR ' + e.message))

  await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(1500)

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

  await page.goto(BASE + '/pages/batch/detail?id=' + bid, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2500)
  await page.screenshot({ path: path.join(OUT, outName) })
  console.log('saved:', path.join(OUT, outName))

  // 质检列表：确认"合格"结论与复核状态并列展示
  await page.goto(BASE + '/pages/quality/list', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2200)
  await page.screenshot({ path: path.join(OUT, 'verify-quality-list.png') })
  console.log('saved quality list')

  // 批次列表：确认"待审"标记出现在卡片上（先筛"种植中"，让待审批次排在前面）
  await page.goto(BASE + '/pages/tabbar/batch', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2200)
  await page.evaluate(() => {
    const els = [...document.querySelectorAll('*')].filter(
      (e) => e.children.length === 0 && (e.textContent || '').trim() === '种植中'
    )
    if (els[0]) els[0].click()
  })
  await sleep(1800)
  await page.screenshot({ path: path.join(OUT, 'verify-batch-list.png') })
  console.log('saved batch list')
  console.log('errors:', errors.length ? errors.slice(0, 5).join('\n') : 'none')
  await browser.close()
})()
