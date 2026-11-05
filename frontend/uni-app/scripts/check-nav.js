/**
 * PC 侧栏切换体检（默认打生产预览 4173）：
 *  - 每 20ms 采样：选中高亮是否中断、内容区是否出现空白帧、快照层是否残留
 *  - 记录「点击 → 新页正文出现」的耗时
 *  - 连续快点 3 个菜单，验证快照不会叠加/残留
 */
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')
const BASE = process.argv[2] || 'http://localhost:4173'
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'

async function fill(p, i, t) {
  const els = await p.$$('input')
  if (!els[i]) return
  await els[i].click({ clickCount: 3 })
  await p.keyboard.type(t, { delay: 25 })
  await new Promise((r) => setTimeout(r, 150))
}

async function clickMenu(p, text) {
  return p.evaluate((t) => {
    const el = Array.from(document.querySelectorAll('.menu-item')).find((x) => (x.textContent || '').includes(t))
    if (el) { el.click(); return true }
    return false
  }, text)
}

;(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox'] })
  const p = await b.newPage()
  const errs = []
  p.on('pageerror', (e) => errs.push('PAGEERROR ' + e.message))
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 140)) })
  await p.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })
  await p.goto(BASE + '/pages/auth/login', { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 900))
  await fill(p, 0, 'admin'); await fill(p, 1, '123456')
  const ok = await p.evaluateHandle(() => Array.from(document.querySelectorAll('.btn')).find((el) => /^登\s*录$/.test((el.textContent || '').trim())))
  if (ok && ok.asElement()) await ok.asElement().click()
  await new Promise((r) => setTimeout(r, 2400))

  async function measure(target, label) {
    const t0 = Date.now()
    await clickMenu(p, target)
    const samples = []
    let readyAt = -1
    for (let i = 0; i < 30; i++) {
      const s = await p.evaluate(() => {
        const on = document.querySelector('.menu-item.on')
        const inner = document.querySelector('.content-inner')
        return {
          on: on ? (on.textContent || '').trim().slice(0, 6) : '',
          len: inner ? (inner.innerText || '').trim().length : -1,
          snap: !!document.getElementById('pc-snap')
        }
      })
      if (readyAt < 0 && s.len > 40) readyAt = Date.now() - t0
      samples.push(s)
      await new Promise((r) => setTimeout(r, 20))
    }
    const blank = samples.filter((s) => s.len === 0).length
    const noHL = samples.filter((s) => !s.on).length
    const snapEnd = samples.filter((s) => s.snap).length
    console.log(`${label}: 正文出现 ${readyAt}ms | 空白帧 ${blank} | 无高亮帧 ${noHL} | 快照在场 ${snapEnd}/${samples.length} 帧`)
  }

  await measure('异常管控', '→ 异常管控')
  await new Promise((r) => setTimeout(r, 500))
  await measure('销售管理', '→ 销售管理')
  await new Promise((r) => setTimeout(r, 500))
  await measure('采收登记', '→ 采收登记')
  await new Promise((r) => setTimeout(r, 500))

  // 连点三个：快照必须始终只有一层，且最终不留残影
  await clickMenu(p, '质量检测')
  await new Promise((r) => setTimeout(r, 40))
  await clickMenu(p, '物流运输')
  await new Promise((r) => setTimeout(r, 40))
  await clickMenu(p, '批次管理')
  await new Promise((r) => setTimeout(r, 900))
  const leftover = await p.evaluate(() => ({
    snaps: document.querySelectorAll('#pc-snap').length,
    veils: document.querySelectorAll('#pc-veil').length,
    on: (document.querySelector('.menu-item.on') || {}).textContent || '',
    url: location.pathname
  }))
  console.log('连点后:', JSON.stringify(leftover))
  console.log('errors =', errs.length, errs.slice(0, 3))
  await b.close()
})()
