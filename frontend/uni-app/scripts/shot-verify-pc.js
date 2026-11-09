/**
 * 临时校验脚本（PC 视口）：登录后截取批次详情与质检列表，确认状态口径一致。
 */
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const path = require('path')

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'https://8-130-105-150.sslip.io'
const OUT = 'C:/Users/30786/Desktop/毕设农产品/docs/assets/screenshots/uni'
const bid = process.argv[2] || '91'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    ignoreHTTPSErrors: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--ignore-certificate-errors']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })
  const errors = []
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

  await page.goto(BASE + '/pages-pc/batch/detail?id=' + bid, { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2500)
  await page.screenshot({ path: path.join(OUT, 'verify-pc-batch-detail.png') })
  console.log('saved pc detail')

  await page.goto(BASE + '/pages-pc/quality/index', { waitUntil: 'networkidle2', timeout: 60000 })
  await sleep(2200)
  await page.screenshot({ path: path.join(OUT, 'verify-pc-quality-list.png') })
  console.log('saved pc quality list')
  console.log('errors:', errors.length ? errors.slice(0, 5).join('\n') : 'none')
  await browser.close()
})()
