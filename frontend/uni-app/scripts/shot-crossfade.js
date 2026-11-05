const path = require('path')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const BASE = 'http://localhost:4173'
const OUT = 'C:/Users/30786/Desktop/毕设农产品/docs/assets/screenshots/uni-pc'
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
async function fill(p, i, t) {
  const els = await p.$$('input'); if (!els[i]) return
  await els[i].click({ clickCount: 3 }); await p.keyboard.type(t, { delay: 20 })
  await new Promise((r) => setTimeout(r, 140))
}
;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage()
  await p.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })
  await p.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 800))
  await fill(p, 0, 'admin'); await fill(p, 1, '123456')
  const ok = await p.evaluateHandle(() => Array.from(document.querySelectorAll('.btn')).find((el) => /^登\s*录$/.test((el.textContent || '').trim())))
  if (ok && ok.asElement()) await ok.asElement().click()
  await new Promise((r) => setTimeout(r, 2200))
  console.log('dashboard:', p.url())

  // 点到「异常管控」，立刻抓帧：此时应看到快照（旧页画面）正在化开
  await p.evaluate(() => {
    const el = Array.from(document.querySelectorAll('.menu-item')).find((x) => (x.textContent || '').includes('异常管控'))
    if (el) el.click()
  })
  // 连续采样 + 抓中间帧
  const t0 = Date.now()
  const frames = []
  for (let i = 0; i < 14; i++) {
    const s = await p.evaluate(() => {
      const snap = document.getElementById('pc-snap')
      const on = document.querySelector('.menu-item.on')
      return {
        snap: snap ? Number(snap.style.opacity || 1) : null,
        on: on ? (on.textContent || '').trim().slice(0, 6) : ''
      }
    })
    frames.push(`${Date.now() - t0}ms snap=${s.snap === null ? 'gone' : s.snap} hl=${s.on || '-'}`)
    await new Promise((r) => setTimeout(r, 30))
  }
  console.log(frames.join('\n'))

  // 重新来一次，卡在快照淡出中段截图
  await p.evaluate(() => {
    const el = Array.from(document.querySelectorAll('.menu-item')).find((x) => (x.textContent || '').includes('质量管理'))
    if (el) el.click()
  })
  await new Promise((r) => setTimeout(r, 150))
  await p.screenshot({ path: path.join(OUT, 'nav-crossfade-mid.png') })
  console.log('mid-frame captured, url =', p.url())
  await b.close()
})()
