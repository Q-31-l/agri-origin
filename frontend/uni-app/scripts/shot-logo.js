const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')
;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const b = await puppeteer.launch({ executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage()
  await p.setViewport({ width: 900, height: 700, deviceScaleFactor: 4 })
  await p.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise(r => setTimeout(r, 1500))
  const el = await p.$('.bl-mark')
  if (el) { await el.screenshot({ path: path.join(OUT, 'ui-logo-big.png') }); console.log('logo ok') }
  else console.log('logo not found')
  await b.close()
})()
