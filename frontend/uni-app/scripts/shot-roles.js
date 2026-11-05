/**
 * 不同角色登录后 PC 侧边栏的差异截图（验证"同一套代码、不同角色不同界面"）
 * 用法：node scripts/shot-roles.js
 */
const path = require('path')
const fs = require('fs')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const BASE = 'http://localhost:5173'
const OUT = path.resolve(__dirname, '../../../docs/assets/screenshots/uni-pc')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

const ACCOUNTS = [
  ['admin', '系统管理员'],
  ['regulator01', '监管人员'],
  ['farmer01', '农户'],
  ['logistics01', '物流企业']
]

async function fillInput(page, index, text) {
  const el = (await page.$$('input'))[index]
  if (!el) return
  await el.click({ clickCount: 3 })
  await page.keyboard.down('Control')
  await page.keyboard.press('KeyA')
  await page.keyboard.up('Control')
  await page.keyboard.press('Backspace')
  await page.keyboard.type(text, { delay: 40 })
  await new Promise((r) => setTimeout(r, 180))
}

;(async () => {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  })

  for (const [user, label] of ACCOUNTS) {
    const page = await browser.newPage()
    await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 })

    await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
    await new Promise((r) => setTimeout(r, 900))
    await fillInput(page, 0, user)
    await fillInput(page, 1, '123456')
    const btn = await page.evaluateHandle(() =>
      Array.from(document.querySelectorAll('.btn')).find((el) => /^登\s*录$/.test((el.textContent || '').trim()))
    )
    if (btn && btn.asElement()) await btn.asElement().click()
    await new Promise((r) => setTimeout(r, 2400))

    const side = await page.$('.side')
    if (side) {
      await side.screenshot({ path: path.join(OUT, `role-${user}.png`) })
      const menu = await page.evaluate(() =>
        Array.from(document.querySelectorAll('.mi-text')).map((e) => e.textContent.trim())
      )
      console.log(user, `(${label})`, menu.length, 'items:', menu.join(' / '))
    } else {
      console.log(user, 'side not found, url =', page.url())
    }
    await page.close()
  }

  await browser.close()
})()
