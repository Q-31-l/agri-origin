const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

const SIZES = [
  ['login-pc-1440', 1440, 900],
  ['login-pc-1024', 1024, 768],
  ['login-mobile', 390, 844]
]

;(async () => {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push('PAGEERROR ' + e.message))
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 160)) })

  for (const [name, w, h] of SIZES) {
    await p.setViewport({ width: w, height: h, deviceScaleFactor: 2 })
    await p.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
    await new Promise((r) => setTimeout(r, 1400))
    await p.screenshot({ path: path.join(OUT, name + '.png') })
    const info = await p.evaluate(() => ({
      w: window.innerWidth,
      pc: !!document.querySelector('.pl'),
      mobile: !!document.querySelector('.login')
    }))
    console.log(name, JSON.stringify(info))
  }
  console.log('errors =', errs.length, errs.slice(0, 4))
  await b.close()
})()
