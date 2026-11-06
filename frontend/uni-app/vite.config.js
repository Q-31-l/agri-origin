import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'node:path'

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    rollupOptions: {
      output: {
        // uni-app 默认按模块路径推导 chunk 名，会产出以 ".." 开头的名字，
        // 对应的 import 引用缺少 './' 前缀，浏览器按裸模块名解析会失败。
        // 这里统一兜底：去掉开头的点与非法字符，保证产物路径始终是相对路径。
        chunkFileNames(chunkInfo) {
          const raw = chunkInfo.name || 'chunk'
          const safe = raw.replace(/^[.\-_]+/, '').replace(/[^\w-]/g, '_') || 'chunk'
          return `assets/${safe}-[hash].js`
        }
      }
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // WebSocket 实时通知走同一前缀：/api/ws
        ws: true
      },
      '/file': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
