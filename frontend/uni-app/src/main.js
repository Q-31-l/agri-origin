import { createSSRApp } from 'vue'
import * as Pinia from 'pinia'
import App from './App.vue'
import { setupStore } from '@/store'
import { isAppUI } from '@/config/platform'
import perm from '@/directives/perm'

/**
 * 路由分包加载失败时自动恢复。
 *
 * 场景：发版后浏览器仍持有旧 index.html，里面引用的分部 chunk 已被新构建替换，
 * 点菜单时动态 import 404。uni-app 会渲染 AsyncError 兜底页
 * （中文文案是「连接服务器超时，点击屏幕重试」），用户表现为"必须手动刷新一次"。
 *
 * 这里在应用层兜一次：捕获到模块加载失败就自动 reload，让浏览器拿到新的
 * index.html。用 sessionStorage 限流，避免资源真缺失时无限刷新。
 *
 * 根治仍然在服务端：index.html 必须 Cache-Control: no-cache（见 ops/nginx-agri-origin.conf）。
 */
const CHUNK_ERR_RE =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|Loading chunk \S+ failed|Importing a module script failed/i

const RELOAD_KEY = '__agri_chunk_reload_at'
const RELOAD_GUARD_MS = 10 * 60 * 1000

function isChunkError(input) {
  if (!input) return false
  const msg = typeof input === 'string' ? input : input.message || String(input)
  return CHUNK_ERR_RE.test(msg)
}

function recoverFromChunkError(reason) {
  try {
    const last = Number(sessionStorage.getItem(RELOAD_KEY) || 0)
    if (Date.now() - last < RELOAD_GUARD_MS) return // 刚重载过，不再循环
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()))
    // eslint-disable-next-line no-console
    console.warn('[chunk] 模块加载失败，自动重载以获取最新资源：', reason)
    window.location.reload()
  } catch (e) {
    /* 隐私模式下 sessionStorage 不可用时忽略 */
  }
}

// #ifdef H5
/*
 * App 版式标记：让全局样式隐藏系统 tabBar（tab 页改用自绘的五格 Dock）。
 * 三端统一 —— 安装包壳、手机浏览器、微信小程序都走这套，只有 PC 宽屏例外。
 * 必须在挂载前打，否则首帧会看到原生 tabBar 闪一下。
 */
if (typeof document !== 'undefined' && isAppUI()) {
  document.body.classList.add('agri-app-shell')
}
/* 窗口从宽屏拖窄（或平板转屏）时补打标记，避免 Dock 与原生 tabBar 同时出现 */
if (typeof window !== 'undefined') {
  let lastAppUI = isAppUI()
  window.addEventListener('resize', () => {
    const now = isAppUI()
    if (now === lastAppUI) return
    lastAppUI = now
    document.body.classList.toggle('agri-app-shell', now)
  })
}

if (typeof window !== 'undefined') {
  window.addEventListener('unhandledrejection', (e) => {
    if (isChunkError(e && e.reason)) recoverFromChunkError(e.reason)
  })
  window.addEventListener('error', (e) => {
    const t = e && (e.error || e.message)
    if (isChunkError(t)) recoverFromChunkError(t)
  })

  /**
   * 上面两个监听只是兜底，真正管用的是这一段。
   *
   * uni-app 的路由懒加载在框架内部就 catch 了 import 失败，不会让 rejection
   * 冒泡到 window，所以监听不到；但它失败后会渲染一个 class 为
   * `uni-async-error` 的占位元素（就是「连接服务器超时，点击屏幕重试」那行字）。
   * 这里直接盯这个元素出现——结果导向，不依赖框架内部的错误传播方式。
   */
  if (typeof MutationObserver !== 'undefined') {
    const WATCH_KEY = '__agri_async_reload_at'
    const WATCH_GUARD_MS = 10 * 60 * 1000
    let scheduled = false

    const checkAsyncError = () => {
      scheduled = false
      if (!document.querySelector('.uni-async-error')) return
      try {
        const last = Number(sessionStorage.getItem(WATCH_KEY) || 0)
        if (Date.now() - last < WATCH_GUARD_MS) return // 刚重载过，别循环
        sessionStorage.setItem(WATCH_KEY, String(Date.now()))
        // eslint-disable-next-line no-console
        console.warn('[async] 分包加载失败，自动重载以获取最新资源')
        window.location.reload()
      } catch (err) {
        /* sessionStorage 不可用时放弃自动恢复，交给用户点重试 */
      }
    }

    const mo = new MutationObserver(() => {
      if (scheduled) return
      scheduled = true
      // 合并同一轮 DOM 变更，避免每次插入节点都查一次
      setTimeout(checkAsyncError, 120)
    })

    const startWatch = () => {
      if (document.body) {
        mo.observe(document.body, { childList: true, subtree: true })
        checkAsyncError()
      }
    }
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', startWatch)
    } else {
      startWatch()
    }
  }
}
// #endif

export function createApp() {
  const app = createSSRApp(App)
  const pinia = Pinia.createPinia()
  app.use(pinia)
  setupStore(pinia)
  app.directive('perm', perm)
  return { app, Pinia }
}
