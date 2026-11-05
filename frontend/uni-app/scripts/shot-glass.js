const path = require('path')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
async function fillInput(page, i, t) {
  const els = await page.$$('input'); if (!els[i]) return
  await els[i].click({ clickCount: 3 }); await page.keyboard.type(t, { delay: 40 })
  await new Promise((r) => setTimeout(r, 200))
}
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage()
  await p.setViewport({ width: 1440, height: 900, deviceScaleFactor: 4 })
  await p.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1000))
  await fillInput(p, 0, 'admin'); await fillInput(p, 1, '123456')
  const ok = await p.evaluateHandle(() => Array.from(document.querySelectorAll('.btn')).find((el) => /^登\s*录$/.test((el.textContent || '').trim())))
  if (ok && ok.asElement()) await ok.asElement().click()
  await new Promise((r) => setTimeout(r, 2200))
  await p.goto(BASE + '/pages-pc/abnormal/index', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1500))
  const r = await p.evaluate(() => { const e = document.querySelector('.menu-item.on'); const b = e.getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height } })
  await p.screenshot({
    path: path.join(OUT, 'ui-glass-item.png'),
    clip: { x: 0, y: r.y - 62, width: 224, height: r.h + 124 }
  })
  console.log('cropped around y=', r.y)
  await b.close()
})()
