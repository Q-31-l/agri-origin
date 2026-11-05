const sharp = require('C:/Users/30786/AppData/Local/Temp/svg2png/node_modules/sharp')
const fs = require('fs')
const path = require('path')

const OUT = 'C:/Users/30786/Desktop/毕设农产品/frontend/uni-app/src/static/tabbar'
fs.mkdirSync(OUT, { recursive: true })

const icons = {
  home: '<path d="M8 26 L24 12 L40 26 V42 H28 V32 H20 V42 H8 Z" fill="none" stroke="COLOR" stroke-width="3.2" stroke-linejoin="round"/>',
  batch: '<rect x="9" y="9" width="13" height="13" rx="2.5" fill="none" stroke="COLOR" stroke-width="3.2"/><rect x="26" y="9" width="13" height="13" rx="2.5" fill="none" stroke="COLOR" stroke-width="3.2"/><rect x="9" y="26" width="13" height="13" rx="2.5" fill="none" stroke="COLOR" stroke-width="3.2"/><rect x="26" y="26" width="13" height="13" rx="2.5" fill="none" stroke="COLOR" stroke-width="3.2"/>',
  trace: '<path d="M11 18 V12 H17 M31 12 H37 V18 M37 30 V36 H31 M17 36 H11 V30" fill="none" stroke="COLOR" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/><path d="M16 24 H32" stroke="COLOR" stroke-width="3.4" stroke-linecap="round"/>',
  mine: '<circle cx="24" cy="17" r="7" fill="none" stroke="COLOR" stroke-width="3.2"/><path d="M10 41 C10 32.7 16.3 27 24 27 C31.7 27 38 32.7 38 41" fill="none" stroke="COLOR" stroke-width="3.2" stroke-linecap="round"/>'
}

const colors = { normal: '#8a94a6', active: '#1f7a4d' }

async function main() {
  for (const [name, body] of Object.entries(icons)) {
    for (const [state, color] of Object.entries(colors)) {
      const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">${body.replace(/COLOR/g, color)}</svg>`
      const file = path.join(OUT, state === 'normal' ? `${name}.png` : `${name}-active.png`)
      await sharp(Buffer.from(svg)).resize(81, 81, { fit: 'contain', background: { r: 255, g: 255, b: 255, alpha: 0 } }).png().toFile(file)
      console.log('ok', file)
    }
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
