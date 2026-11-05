/**
 * 静默续期（refresh token）端到端验证
 *
 * 步骤：
 *   1. 正常登录，拿到 access + refresh
 *   2. 把 localStorage 里的 access token 改成非法值（模拟"过期"），refresh 保留
 *   3. 刷新页面，观察：是否自动续期并正常拿到数据，而不是被踢回登录页
 *   4. 反向验证：access 和 refresh 都清掉时，必须回到登录页
 */
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const BASE = 'http://localhost:5173'
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

const ok = (b) => (b ? 'PASS' : 'FAIL')

async function fillInput(page, index, text, tries = 3) {
  for (let i = 0; i < tries; i++) {
    const inputs = await page.$$('input')
    const el = inputs[index]
    if (!el) return
    await el.click({ clickCount: 3 })
    await page.keyboard.down('Control')
    await page.keyboard.press('KeyA')
    await page.keyboard.up('Control')
    await page.keyboard.press('Backspace')
    await page.keyboard.type(text, { delay: 40 })
    await new Promise((r) => setTimeout(r, 260))
    const v = await page.evaluate((idx) => {
      const list = document.querySelectorAll('input')
      return list[idx] ? list[idx].value : ''
    }, index)
    if (v === text) return
  }
}

/** uni 的 H5 storage 外层包了 {type,data}，这里统一拆包 */
async function readAuth(page) {
  return page.evaluate(() => {
    const pick = (k) => {
      const raw = localStorage.getItem(k)
      if (!raw) return ''
      const o = JSON.parse(raw)
      return o && o.data ? o.data.v : o && o.v ? o.v : o
    }
    return {
      token: pick('agri_token'),
      refresh: pick('agri_refresh_token'),
      exp: pick('agri_token_exp')
    }
  })
}

;(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    defaultViewport: { width: 1440, height: 900 },
    args: ['--no-sandbox']
  })
  const page = await browser.newPage()
  const errors = []
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text())
  })

  // 1) 登录
  await page.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await fillInput(page, 0, 'admin')
  await fillInput(page, 1, '123456')
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll('.btn')]
    const b = btns.find((x) => /^登\s*录$/.test(x.textContent.trim()))
    if (b) b.click()
  })
  await page.waitForFunction(() => location.pathname.indexOf('pages-pc') >= 0, { timeout: 20000 })
  await new Promise((r) => setTimeout(r, 1200))

  const before = await readAuth(page)
  console.log('登录后 token 长度 =', before.token.length, '| refresh 长度 =', before.refresh.length)
  console.log('[1] 登录获取双 token ............', ok(before.token && before.refresh))

  // 2) 把 access 改成非法值，refresh 保留
  await page.evaluate(() => {
    const k = 'agri_token'
    const o = JSON.parse(localStorage.getItem(k))
    o.data.v = 'invalid.access.token'
    localStorage.setItem(k, JSON.stringify(o))
  })

  // 3) 刷新页面：应静默续期
  await page.reload({ waitUntil: 'networkidle2' })
  await page.goto(BASE + '/pages-pc/batch/list', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 2500))

  const after = await readAuth(page)
  const rows = await page.evaluate(() => document.querySelectorAll('.pc-tr, .row').length)
  const onLogin = await page.evaluate(() => location.pathname.indexOf('auth/login') >= 0)

  console.log('续期后 token 长度 =', after.token.length, '| 已换新 =', after.token !== 'invalid.access.token')
  console.log('当前路径是登录页 =', onLogin, '| 列表行数 =', rows)
  console.log('[2] 401 后静默续期不跳登录 ......', ok(!onLogin && after.token !== 'invalid.access.token'))
  console.log('[3] 续期后业务数据仍可加载 ......', ok(rows > 0))

  // 4) 反向：清空 refresh 再看是否回登录页
  await page.evaluate(() => {
    localStorage.removeItem('agri_refresh_token')
    const k = 'agri_token'
    const o = JSON.parse(localStorage.getItem(k))
    o.data.v = 'invalid.access.token'
    localStorage.setItem(k, JSON.stringify(o))
  })
  await page.goto(BASE + '/pages-pc/batch/list', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 2500))
  const kicked = await page.evaluate(() => location.pathname.indexOf('auth/login') >= 0)
  console.log('[4] 无 refresh 时正确踢回登录页 ....', ok(kicked))

  if (errors.length) {
    console.log('\nconsole errors:')
    errors.slice(0, 10).forEach((e) => console.log(' -', e))
  } else {
    console.log('\nconsole errors: 0')
  }

  await browser.close()
})()
