/**
 * 把品牌矢量的 logo 渲染成 PNG，供不便使用内联 SVG 的端（小程序 / App）使用。
 * 源文件：src/static/logo/mark.svg
 * 产物：src/static/logo/mark.png（256×256，最大显示 64px @4x 仍清晰）
 */
const fs = require('fs')
const path = require('path')
const puppeteer = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/puppeteer-core')

const DIR = path.resolve(__dirname, '../src/static/logo')
const CHROME = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const SIZE = 256

;(async () => {
  const svg = fs.readFileSync(path.join(DIR, 'mark.svg'), 'utf8')
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: 'new',
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: SIZE, height: SIZE, deviceScaleFactor: 1 })
  await page.setContent(
    `<html><body style="margin:0;background:transparent">
      <div id="wrap" style="width:${SIZE}px;height:${SIZE}px">${svg.replace(
        /width="512" height="512"/,
        `width="${SIZE}" height="${SIZE}"`
      )}</div>
    </body></html>`,
    { waitUntil: 'load' }
  )
  const el = await page.$('#wrap')
  const file = path.join(DIR, 'mark.png')
  await el.screenshot({ path: file, omitBackground: true })
  console.log('OK', file, fs.statSync(file).size, 'bytes')

  await browser.close()
})()
