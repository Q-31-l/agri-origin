/**
 * 开发期辅助：从 pages.full.json 生成 pages.json，剔除尚未创建 .vue 的页面。
 *
 * 注意：pages.json 中带有 `// #ifdef H5 / MP-WEIXIN / APP-PLUS` 条件编译标记，
 * 一旦用 JSON.parse → JSON.stringify 往返就会丢失这些注释，导致小程序包里混入
 * PC 页面（或反过来）。因此这里全部按「文本」处理，保留注释与缩进。
 *
 * 用法：
 *   node scripts/gen-pages.js            # 生成（剔除缺失页面）
 *   node scripts/gen-pages.js --restore  # 从完整版恢复
 */
const fs = require('fs')
const path = require('path')

const SRC = path.resolve(__dirname, '../src')
const FULL = path.join(SRC, 'pages.full.json')
const OUT = path.join(SRC, 'pages.json')

/* ---------------- 文本工具 ---------------- */

/** 从 start（指向 '[' 或 '{'）开始，返回与之匹配的结束下标（含） */
function matchBracket(text, start) {
  const open = text[start]
  const close = open === '[' ? ']' : '}'
  let depth = 0
  let inStr = false
  let esc = false
  for (let i = start; i < text.length; i++) {
    const ch = text[i]
    if (inStr) {
      if (esc) esc = false
      else if (ch === '\\') esc = true
      else if (ch === '"') inStr = false
      continue
    }
    if (ch === '"') {
      inStr = true
      continue
    }
    if (ch === open) depth++
    else if (ch === close) {
      depth--
      if (depth === 0) return i
    }
  }
  return -1
}

/** 找到 key（如 "pages"）后紧跟的数组起始下标 */
function findArrayOf(text, key) {
  const re = new RegExp(`"${key}"\\s*:\\s*\\[`)
  const m = re.exec(text)
  if (!m) return -1
  return m.index + m[0].length - 1
}

/** 在 [from, to] 范围内，找出所有顶层对象（相对深度 1）的 [start, end] */
function topLevelObjects(text, from, to) {
  const spans = []
  let depth = 0
  let inStr = false
  let esc = false
  let objStart = -1
  for (let i = from; i <= to; i++) {
    const ch = text[i]
    if (inStr) {
      if (esc) esc = false
      else if (ch === '\\') esc = true
      else if (ch === '"') inStr = false
      continue
    }
    if (ch === '"') {
      inStr = true
      continue
    }
    if (ch === '{') {
      depth++
      if (depth === 1) objStart = i
    } else if (ch === '}') {
      if (depth === 1) spans.push([objStart, i])
      depth--
    }
  }
  return spans
}

/** 读取对象文本里的某个字符串字段 */
function fieldOf(text, start, end, key) {
  const seg = text.slice(start, end + 1)
  const re = new RegExp(`"${key}"\\s*:\\s*"([^"]*)"`)
  const m = re.exec(seg)
  return m ? m[1] : ''
}

/**
 * 从 text 中删除 [start, end] 这一段（含随后或之前的逗号与空白）。
 * 从后往前删，避免下标位移。
 */
function removeSpanWithComma(text, start, end) {
  let s = start
  let e = end
  // 向前吃掉空白
  let i = s - 1
  while (i >= 0 && /\s/.test(text[i])) i--
  if (text[i] === ',') {
    s = i
  } else {
    // 末尾元素：吃掉后面的逗号
    let j = e + 1
    while (j < text.length && /\s/.test(text[j])) j++
    if (text[j] === ',') e = j
  }
  return text.slice(0, s) + text.slice(e + 1)
}

/* ---------------- 主流程 ---------------- */

function existsPage(p) {
  return fs.existsSync(path.join(SRC, p + '.vue'))
}

function main() {
  const restore = process.argv.includes('--restore')

  if (!fs.existsSync(FULL)) {
    fs.copyFileSync(OUT, FULL)
    console.log('已备份 pages.json -> pages.full.json')
  }

  if (restore) {
    fs.copyFileSync(FULL, OUT)
    console.log('已恢复完整 pages.json')
    return
  }

  let text = fs.readFileSync(FULL, 'utf8')

  /* ---- 1. 过滤 pages 数组 ---- */
  const arrStart = findArrayOf(text, 'pages')
  if (arrStart < 0) throw new Error('pages.full.json 中未找到 pages 数组')
  const arrEnd = matchBracket(text, arrStart)

  const missing = []
  const spans = topLevelObjects(text, arrStart + 1, arrEnd - 1)
  const toRemove = []
  for (const [s, e] of spans) {
    const p = fieldOf(text, s, e, 'path')
    if (!p) continue
    if (!existsPage(p)) {
      missing.push(p)
      toRemove.push([s, e])
    }
  }
  // 从后往前删除
  toRemove.sort((a, b) => b[0] - a[0])
  for (const [s, e] of toRemove) {
    text = removeSpanWithComma(text, s, e)
  }

  /* ---- 2. 过滤 tabBar.list 中已不存在的页面 ---- */
  const tbStart = findArrayOf(text, 'list')
  if (tbStart > 0) {
    const tbEnd = matchBracket(text, tbStart)
    const tbSpans = topLevelObjects(text, tbStart + 1, tbEnd - 1)
    const alive = new Set(
      topLevelObjects(text, findArrayOf(text, 'pages') + 1, matchBracket(text, findArrayOf(text, 'pages')) - 1)
        .map(([s, e]) => fieldOf(text, s, e, 'path'))
        .filter(Boolean)
    )
    const tbRemove = []
    for (const [s, e] of tbSpans) {
      const p = fieldOf(text, s, e, 'pagePath')
      if (p && !alive.has(p)) tbRemove.push([s, e])
    }
    tbRemove.sort((a, b) => b[0] - a[0])
    for (const [s, e] of tbRemove) {
      text = removeSpanWithComma(text, s, e)
      console.log('  tabBar 移除：' + fieldOf(text, s, e, 'pagePath'))
    }
  }

  /* ---- 3. 基本合法性校验 ---- */
  const stripped = text
    .replace(/^\s*\/\/.*$/gm, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/,(\s*[}\]])/g, '$1')
  try {
    JSON.parse(stripped)
  } catch (e) {
    console.error('生成的 pages.json 不是合法 JSON：' + e.message)
    process.exit(1)
  }

  fs.writeFileSync(OUT, text, 'utf8')

  const count = (text.match(/"path"\s*:/g) || []).length
  console.log(`已生成 pages.json：${count} 个页面（保留 #ifdef 条件编译标记）`)
  if (missing.length) {
    console.log(`跳过未创建页面 ${missing.length} 个：`)
    missing.slice(0, 12).forEach((m) => console.log('  - ' + m))
    if (missing.length > 12) console.log('  ...')
  }
}

main()
