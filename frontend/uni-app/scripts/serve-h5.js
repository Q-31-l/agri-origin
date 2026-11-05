/**
 * 本地预览「生产构建」的 H5：
 *  - 静态托管 dist/build/h5（带 SPA history 回退）
 *  - /api 反向代理到 http://localhost:8080，避免跨域
 *
 * dev 服务器（5173）是按需编译的，切页第一次会现编译，天然有顿挫；
 * 生产构建是预打包好的 chunk，切页只剩网络/渲染耗时，流畅度才是上线后的真实水平。
 *
 * 用法：node scripts/serve-h5.js [port]   （默认 4173）
 */
const http = require('http')
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '../dist/build/h5')
const PORT = Number(process.argv[2] || 4173)
const API = 'http://localhost:8080'

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.mp4': 'video/mp4',
  '.map': 'application/json'
}

if (!fs.existsSync(path.join(ROOT, 'index.html'))) {
  console.error('[serve-h5] 未找到 dist/build/h5/index.html，请先执行 npm run build:h5')
  process.exit(1)
}

const server = http.createServer((req, res) => {
  const url = decodeURIComponent((req.url || '/').split('?')[0])

  // 接口代理
  if (url.startsWith('/api')) {
    const proxy = http.request(
      API + req.url,
      { method: req.method, headers: { ...req.headers, host: 'localhost:8080' } },
      (pr) => {
        res.writeHead(pr.statusCode, pr.headers)
        pr.pipe(res)
      }
    )
    proxy.on('error', () => {
      res.writeHead(502)
      res.end('backend unavailable')
    })
    req.pipe(proxy)
    return
  }

  let file = path.join(ROOT, url)
  if (!file.startsWith(ROOT)) {
    res.writeHead(403)
    return res.end()
  }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    // SPA 回退：带扩展名的缺失资源直接 404，其余一律回 index.html
    if (path.extname(url)) {
      res.writeHead(404)
      return res.end('not found')
    }
    file = path.join(ROOT, 'index.html')
  }
  const ext = path.extname(file).toLowerCase()
  const headers = { 'Content-Type': MIME[ext] || 'application/octet-stream' }
  // 带 hash 的静态资源长缓存，index.html 不缓存
  if (/assets[\\/]/.test(file)) headers['Cache-Control'] = 'public, max-age=31536000, immutable'
  res.writeHead(200, headers)
  fs.createReadStream(file).pipe(res)
})

server.listen(PORT, () => {
  console.log(`[serve-h5] production preview: http://localhost:${PORT}  (api -> ${API})`)
})
