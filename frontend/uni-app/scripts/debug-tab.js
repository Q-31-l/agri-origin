const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

;(async () => {
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true })
  await page.goto('http://127.0.0.1:5173/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1200))

  const info = await page.evaluate(() => {
    const tabs = Array.from(document.querySelectorAll('.tabs .tab'))
    return {
      count: tabs.length,
      items: tabs.map((el) => {
        const cs = getComputedStyle(el)
        const r = el.getBoundingClientRect()
        return {
          text: el.textContent.trim(),
          color: cs.color,
          fontSize: cs.fontSize,
          display: cs.display,
          visibility: cs.visibility,
          opacity: cs.opacity,
          rect: { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) },
          parentOverflow: (() => { const p = el.parentElement; const c = getComputedStyle(p); return { overflow: c.overflow, h: Math.round(p.getBoundingClientRect().height) } })(),
          cardBox: (() => { const c = el.closest('.card'); const r2 = c.getBoundingClientRect(); return { y: Math.round(r2.y), h: Math.round(r2.height), pad: getComputedStyle(c).paddingTop } })()
        }
      })
    }
  })
  console.log(JSON.stringify(info, null, 2))
  await browser.close()
})()
