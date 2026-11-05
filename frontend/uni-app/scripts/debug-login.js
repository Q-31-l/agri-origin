const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

;(async () => {
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true })
  page.on('console', (m) => console.log('[console]', m.type(), m.text().slice(0, 200)))
  page.on('requestfailed', (r) => console.log('[reqfail]', r.url()))
  page.on('response', (r) => { if (r.status() >= 400) console.log('[http]', r.status(), r.url()) })

  await page.goto('http://127.0.0.1:5173/pages/auth/login', { waitUntil: 'networkidle2' })
  await sleep(1500)

  const dom = await page.evaluate(() => {
    const ins = Array.from(document.querySelectorAll('input'))
    const btn = Array.from(document.querySelectorAll('.btn')).map((b) => b.textContent.trim())
    return { inputCount: ins.length, types: ins.map((i) => i.type), btn }
  })
  console.log('DOM:', JSON.stringify(dom))

  // 方式：点击后逐字符输入
  const inputs = await page.$$('input')
  if (inputs.length >= 2) {
    await inputs[0].click()
    await page.keyboard.type('admin', { delay: 40 })
    await inputs[1].click()
    await page.keyboard.type('123456', { delay: 40 })
  }
  const vals = await page.evaluate(() => Array.from(document.querySelectorAll('input')).map((i) => i.value))
  console.log('values after type:', vals)

  const btn = await page.$('.btn')
  if (btn) await btn.click()
  await sleep(3000)
  console.log('url after click:', page.url())
  console.log('storage:', await page.evaluate(() => JSON.stringify(Object.keys(localStorage).map((k) => [k, (localStorage.getItem(k) || '').slice(0, 60)]))))
  console.log('body:', (await page.evaluate(() => document.body.innerText)).replace(/\n+/g, ' | ').slice(0, 300))

  await browser.close()
})()
