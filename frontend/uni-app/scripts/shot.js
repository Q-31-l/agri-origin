const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const fs = require('fs')
const path = require('path')

const OUT = 'C:/Users/30786/Desktop/毕设农产品/docs/assets/screenshots/uni'
fs.mkdirSync(OUT, { recursive: true })

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'http://127.0.0.1:5173'

const shots = process.argv[2] === 'pc'
  ? [{ name: 'pc-login', url: '/pages/auth/login', w: 1440, h: 900 }]
  : [
      { name: 'm-login', url: '/pages/auth/login', w: 390, h: 844 },
      { name: 'm-launch', url: '/', w: 390, h: 844, wait: 150 }
    ]

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--force-device-scale-factor=2']
  })

  const errors = []
  for (const s of shots) {
    const page = await browser.newPage()
    await page.setViewport({ width: s.w, height: s.h, deviceScaleFactor: 2, isMobile: s.w < 900, hasTouch: s.w < 900 })
    page.on('console', (m) => {
      if (m.type() === 'error') errors.push(`[${s.name}] ${m.text()}`)
    })
    page.on('pageerror', (e) => errors.push(`[${s.name}] PAGEERROR ${e.message}`))

    try {
      await page.goto(BASE + s.url, { waitUntil: 'networkidle2', timeout: 45000 })
      await new Promise((r) => setTimeout(r, s.wait || 1500))
      const file = path.join(OUT, s.name + '.png')
      await page.screenshot({ path: file })
      console.log('OK', file)
      const text = await page.evaluate(() => document.body.innerText.slice(0, 300))
      console.log('TEXT:', text.replace(/\n+/g, ' | ').slice(0, 240))
    } catch (e) {
      console.log('FAIL', s.name, e.message)
    }
    await page.close()
  }

  if (errors.length) {
    console.log('--- console errors ---')
    errors.slice(0, 20).forEach((e) => console.log(e))
  } else {
    console.log('no console errors')
  }
  await browser.close()
})()
