<template>
  <view class="pc-shell">
    <view v-if="loggedIn" class="side">
      <view class="brand">
        <brand-logo size="sm" tone="light" />
      </view>

      <scroll-view
        scroll-y
        class="menu"
        :scroll-top="scrollTop"
        :show-scrollbar="false"
        @scroll="onMenuScroll"
      >
        <block v-for="(m, i) in menus" :key="i">
          <view v-if="m.group" class="group">{{ m.group }}</view>
          <view v-else class="menu-item" :class="{ on: current === m.path }" @click="go(m.path)">
            <view class="mi-glass"></view>
            <pc-icon :name="m.icon" :size="17" class="mi-icon" />
            <text class="mi-text">{{ m.title }}</text>
          </view>
        </block>
      </scroll-view>

      <view class="side-foot">
        <text class="sf-text">FISCO-BCOS 联盟链</text>
        <text class="sf-sub">v{{ version }}</text>
      </view>
    </view>

    <view class="main">
      <view class="topbar">
        <view class="crumbs">
          <text class="c-root">农安链溯源</text>
          <text class="c-sep">/</text>
          <text class="c-cur">{{ title }}</text>
        </view>
        <view class="top-right">
          <template v-if="loggedIn">
            <text class="chain-state" :class="{ off: !chainOn }">
              <text class="dot"></text>{{ chainOn ? '链服务正常' : '演示模式' }}
            </text>
            <view class="user" @click="goMine">
              <text class="u-avatar">{{ avatar }}</text>
              <view class="col">
                <text class="u-name">{{ store.displayName }}</text>
                <text class="u-role">{{ store.roleName }}</text>
              </view>
            </view>
            <text class="logout" @click="onLogout">退出</text>
          </template>
          <text v-else class="login-entry" @click="goLogin">登录管理后台 ›</text>
        </view>
      </view>

      <scroll-view scroll-y class="content">
        <view class="content-inner">
          <slot></slot>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BrandLogo from '@/components/common/brand-logo.vue'
import PcIcon from '@/components/pc/pc-icon.vue'
import { PC_MENUS } from '@/config/routes'
import { useUserStore } from '@/store/user'
import { chainStatus } from '@/api'
import { ENV } from '@/config/env'
import { confirm } from '@/utils/ui'

defineProps({ title: { type: String, default: '' } })

const store = useUserStore()
const version = ENV.version
const chainOn = ref(true)
const current = ref('')

/**
 * 这个外壳既服务于登录后的管理后台，也包着公开的「溯源查询」页。
 * 未登录时不能渲染侧栏，更不能去调 /chain/status——那是需要鉴权的接口，
 * 401 会被请求拦截器当成"登录失效"整页踢回登录页，消费者就查不了溯源码。
 */
const loggedIn = computed(() => store.isLogin)

const avatar = computed(() => (store.displayName || 'U').slice(0, 1))

/**
 * 菜单按权限裁剪；某个分组下若一项都没剩下，连分组标题一起隐藏，
 * 避免出现"系统"下面只挂一项、或空分组的尴尬。
 */
const menus = computed(() => {
  const buckets = []
  let cur = null
  for (const m of PC_MENUS) {
    if (m.group) {
      cur = { group: m.group, items: [] }
      buckets.push(cur)
    } else if (!m.perm || store.hasPerm(m.perm)) {
      if (cur) {
        cur.items.push(m)
      } else {
        buckets.push(m)
      }
    }
  }
  const flat = []
  for (const b of buckets) {
    if (b.group) {
      if (b.items.length) {
        flat.push({ group: b.group })
        flat.push(...b.items)
      }
    } else {
      flat.push(b)
    }
  }
  return flat
})

/**
 * 侧栏滚动位置：模块级变量，跨页面导航（reLaunch 会重建页面）后仍能复原，
 * 否则每次切换菜单都会跳回顶部，视觉上像"整页刷新"。
 */
let menuScrollTop = 0
const scrollTop = ref(0)

/**
 * PC 切页转场：整页快照渐变（snapshot crossfade）。
 *
 * uni.reLaunch 会销毁整个页面再重建，中间有 ~80ms 空档；骨架屏能遮住空档，
 * 但"旧内容瞬间消失 → 骨架 → 新内容"本身仍是一跳。这里更进一步：
 * 点击菜单时把当前整个画面 DOM 深拷贝成一层 fixed 快照（canvas 逐帧拷贝像素，
 * ECharts 图表也不会留白），旧页面销毁后用户看到的仍是"刚才那一屏"，
 * 新页面在快照下面静默加载，然后快照以 0.26s 缓缓淡出——视觉上是一次连续的化开，
 * 没有空白、没有骨架、没有跳变。
 *
 * 快照失败（极端老内核）时退化为骨架遮罩。仅 H5 需要（小程序 / App 是原生多页）。
 */
// #ifdef H5
function takeSnapshot() {
  try {
    const prev = document.getElementById('pc-snap')
    if (prev) prev.remove()
    const snap = document.createElement('div')
    snap.id = 'pc-snap'
    snap.style.cssText =
      'position:fixed;inset:0;z-index:9997;background:#f2f4f7;overflow:hidden;pointer-events:none;opacity:1;'
    // 深拷贝当前画面（跳过脚本与上一层的转场元素）
    const pairs = []
    for (const node of Array.from(document.body.childNodes)) {
      if (node.nodeType !== 1) continue
      if (node.tagName === 'SCRIPT') continue
      if (node.id === 'pc-veil' || node.id === 'pc-snap') continue
      const c = node.cloneNode(true)
      snap.appendChild(c)
      pairs.push([node, c])
    }
    if (!snap.childNodes.length) return false
    // canvas 深拷贝只复制标签不复制像素，逐帧把图画过去
    for (const [src, dst] of pairs) {
      if (!src.querySelectorAll) continue
      const sCanvases = src.querySelectorAll('canvas')
      const dCanvases = dst.querySelectorAll('canvas')
      sCanvases.forEach((cv, i) => {
        try {
          const cc = dCanvases[i]
          if (!cc) return
          cc.width = cv.width
          cc.height = cv.height
          cc.getContext('2d').drawImage(cv, 0, 0)
        } catch (e) {
          /* 跨域 canvas 拷贝失败时留白即可，不影响整体 */
        }
      })
    }
    document.body.appendChild(snap)
    return true
  } catch (e) {
    return false
  }
}

function releaseSnapshot() {
  const snap = document.getElementById('pc-snap')
  if (!snap) return false
  // 等两帧：让新页面完成首次绘制，再让快照缓缓化开
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      snap.style.transition = 'opacity 0.26s ease'
      snap.style.opacity = '0'
      setTimeout(() => snap.remove(), 300)
    })
  })
  return true
}

function showVeil() {
  if (document.getElementById('pc-veil')) return
  const el = document.createElement('div')
  el.id = 'pc-veil'
  el.style.cssText =
    'position:fixed;inset:0;z-index:9998;background:#f2f4f7;pointer-events:none;opacity:1;transition:opacity .16s ease'
  // 骨架：深绿侧栏 + 白色顶栏 + 几个占位块，让遮罩"像页面"而不是一块色板
  let bars = ''
  for (let i = 0; i < 7; i++) {
    bars +=
      '<div style="position:absolute;left:20px;top:' +
      (104 + i * 40) +
      'px;width:130px;height:9px;border-radius:4px;background:rgba(255,255,255,0.07)"></div>'
  }
  el.innerHTML =
    '<div style="position:absolute;left:0;top:0;bottom:0;width:224px;background:linear-gradient(180deg,#14432c 0%,#0f3522 100%)">' +
    '<div style="position:absolute;left:16px;top:17px;width:34px;height:34px;border-radius:9px;background:rgba(255,255,255,0.12)"></div>' +
    bars +
    '</div>' +
    '<div style="position:absolute;left:224px;top:0;right:0;height:60px;background:#fff;border-bottom:1px solid #e6ebf0">' +
    '<div style="position:absolute;left:24px;top:25px;width:150px;height:9px;border-radius:4px;background:#eef2f6"></div>' +
    '</div>'
  document.body.appendChild(el)
  // 兜底：极端情况下导航失败，别让遮罩留在页面上
  setTimeout(() => {
    const cur = document.getElementById('pc-veil')
    if (cur && cur.style.opacity === '1') cur.remove()
  }, 1200)
}

function hideVeil() {
  const el = document.getElementById('pc-veil')
  if (!el) return
  el.style.opacity = '0'
  setTimeout(() => el.remove(), 220)
}
// #endif

function onMenuScroll(e) {
  menuScrollTop = (e.detail && e.detail.scrollTop) || 0
}

onMounted(() => {
  if (menuScrollTop > 0) {
    nextTick(() => {
      scrollTop.value = menuScrollTop
    })
  }
  // #ifdef H5
  // 优先化开整页快照；没有快照（例如从登录页进来）才走骨架淡出
  if (!releaseSnapshot()) hideVeil()
  else {
    const v = document.getElementById('pc-veil')
    if (v) v.remove()
  }
  // #endif
})

function routePath() {
  const pages = getCurrentPages()
  const p = pages[pages.length - 1]
  return '/' + (p && p.route ? p.route : '')
}

function syncCurrent() {
  current.value = routePath()
}

// onLoad 时页面已入栈，直接取路由：不要用 setTimeout 延迟，
// 否则每次切换菜单高亮都会"先灭后亮"，看起来就是闪烁。
onLoad(() => {
  syncCurrent()
})

function go(path) {
  if (!path || path === current.value) return
  // #ifdef H5
  // 先冻结当前画面做渐变底；快照失败才退化为骨架遮罩
  if (!takeSnapshot()) showVeil()
  // #endif
  uni.reLaunch({ url: path })
}

function goMine() {
  uni.reLaunch({ url: '/pages/tabbar/mine' })
}

function goLogin() {
  uni.reLaunch({ url: '/pages/auth/login' })
}

async function onLogout() {
  const ok = await confirm('确定退出登录吗？', '退出')
  if (!ok) return
  await store.logout()
  uni.reLaunch({ url: '/pages/auth/login' })
}

if (store.isLogin) {
  chainStatus()
    .then((r) => {
      chainOn.value = !!(r.data && r.data.enabled)
    })
    .catch(() => {
      chainOn.value = false
    })
}
</script>

<style lang="scss" scoped>
.pc-shell {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: row;
  background: #f2f4f7;
  overflow: hidden;
}
.side {
  position: relative;
  width: 224px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  /* 三层光斑 + 底色：既是视觉层次，也给毛玻璃提供了可模糊的内容 */
  background:
    radial-gradient(520px 300px at 10% -8%, rgba(126, 226, 168, 0.22) 0%, rgba(126, 226, 168, 0) 62%),
    radial-gradient(340px 340px at 52% 46%, rgba(94, 214, 146, 0.16) 0%, rgba(94, 214, 146, 0) 70%),
    radial-gradient(420px 340px at 108% 104%, rgba(56, 189, 248, 0.13) 0%, rgba(56, 189, 248, 0) 60%),
    linear-gradient(180deg, #14432c 0%, #0f3522 100%);
  z-index: 1;
}
.side::before {
  /* 极淡的斜向纹理：选中项的 backdrop-filter 会把它糊开，
     玻璃"透出并模糊了背后的东西"这件事才看得出来，否则只是块纯色胶囊 */
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      115deg,
      rgba(255, 255, 255, 0.055) 0 1.5px,
      rgba(255, 255, 255, 0) 1.5px 24px
    ),
    repeating-linear-gradient(
      25deg,
      rgba(255, 255, 255, 0.035) 0 1.5px,
      rgba(255, 255, 255, 0) 1.5px 36px
    );
  opacity: 0.9;
}
.brand,
.menu,
.side-foot {
  position: relative;
  z-index: 2;
}
.side::after {
  /* 极细的竖向高光，让侧栏折角处有一点玻璃棱边 */
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.16) 0%, rgba(255, 255, 255, 0.02) 46%, rgba(255, 255, 255, 0.1) 100%);
  pointer-events: none;
}
.brand {
  height: 68px;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.menu {
  flex: 1;
  padding: 12px 10px;
  overflow: hidden;
}
.group {
  padding: 14px 12px 6px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.38);
  letter-spacing: 1px;
}
.menu-item {
  position: relative;
  height: 38px;
  border-radius: 9px;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 12px;
  margin-bottom: 2px;
  cursor: pointer;
  overflow: hidden;
  transition: background-color 0.2s ease, box-shadow 0.25s ease, transform 0.18s ease;
}

/* hover：一层很淡的磨砂，鼠标划过有"玻璃被轻轻擦亮"的感觉 */
.menu-item:hover {
  background: rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}
.menu-item:hover .mi-glass {
  opacity: 0.55;
}

/* ---------------- 选中态：毛玻璃 ---------------- */
.menu-item.on {
  background: rgba(255, 255, 255, 0.03);
  box-shadow:
    0 10px 24px -10px rgba(0, 0, 0, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.55),
    inset 0 -1px 0 rgba(255, 255, 255, 0.12),
    inset 1px 0 0 rgba(255, 255, 255, 0.2),
    inset 0 0 0 1px rgba(255, 255, 255, 0.16);
}
.menu-item.on .mi-glass {
  opacity: 1;
}

/* 玻璃主体：多段亮度的高光带 + 背景模糊，模拟真实玻璃的折射条纹 */
.mi-glass {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.22s ease;
  background:
    linear-gradient(
      158deg,
      rgba(255, 255, 255, 0.17) 0%,
      rgba(255, 255, 255, 0.07) 38%,
      rgba(255, 255, 255, 0.045) 62%,
      rgba(255, 255, 255, 0.12) 100%
    ),
    radial-gradient(110% 140% at 4% -16%, rgba(226, 255, 238, 0.22) 0%, rgba(226, 255, 238, 0) 54%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.65),
    inset 0 -1px 0 rgba(255, 255, 255, 0.14),
    inset 0 0 0 1px rgba(255, 255, 255, 0.22),
    inset 0 -10px 16px -12px rgba(0, 0, 0, 0.45);
  -webkit-backdrop-filter: blur(12px) saturate(165%) brightness(1.1);
  backdrop-filter: blur(12px) saturate(165%) brightness(1.1);
}
/* 选中项左侧的发光指示条 */
.menu-item.on .mi-glass::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 19px;
  margin-top: -9.5px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #d5fbe6 0%, #34d399 100%);
  box-shadow: 0 0 10px rgba(52, 211, 153, 0.85);
}
/* 上半部一层柔和的反射光：玻璃最重要的"亮面"就来自这里 */
.menu-item.on .mi-glass::after {
  content: '';
  position: absolute;
  left: 1px;
  right: 1px;
  top: 0;
  height: 58%;
  border-radius: 9px 9px 55% 55% / 9px 9px 100% 100%;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.34) 0%,
    rgba(255, 255, 255, 0.1) 42%,
    rgba(255, 255, 255, 0) 100%
  );
}

.mi-icon {
  position: relative;
  z-index: 2;
  margin-right: 9px;
  color: rgba(255, 255, 255, 0.58);
  transition: color 0.2s ease, transform 0.25s ease;
}
.menu-item:hover .mi-icon {
  color: rgba(255, 255, 255, 0.85);
  transform: translateX(1px);
}
.menu-item.on .mi-icon {
  color: #a9f0c6;
}
.mi-text {
  position: relative;
  z-index: 2;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
  letter-spacing: 0.4px;
  transition: color 0.2s ease;
}
.menu-item.on .mi-text {
  color: #fff;
  font-weight: 500;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
}
.side-foot {
  padding: 14px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.sf-text {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
}
.sf-sub {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.3);
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.login-entry {
  font-size: 13px;
  color: #1f7a4d;
  cursor: pointer;
  padding: 6px 12px;
  border: 1px solid rgba(31, 122, 77, 0.28);
  border-radius: 16px;
  white-space: nowrap;
}
.login-entry:hover {
  background: #e8f5ee;
}
.topbar {
  height: 60px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e6ebf0;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.crumbs {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.c-root {
  font-size: 13px;
  color: #94a3b8;
}
.c-sep {
  font-size: 13px;
  color: #cbd5e1;
}
.c-cur {
  font-size: 13px;
  color: #1f2937;
  font-weight: 500;
}
.top-right {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 18px;
}
.chain-state {
  display: flex;
  flex-direction: row;
  align-items: center;
  font-size: 12px;
  color: #16a34a;
  background: #e8f7ee;
  padding: 4px 10px;
  border-radius: 12px;
}
.chain-state.off {
  color: #b45309;
  background: #fef6e0;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: currentColor;
  margin-right: 6px;
}
.user {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.u-avatar {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  background: #1f7a4d;
  color: #fff;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.u-name {
  font-size: 13px;
  color: #1f2937;
  line-height: 1.2;
}
.u-role {
  font-size: 10px;
  color: #94a3b8;
  line-height: 1.2;
}
.logout {
  font-size: 12px;
  color: #94a3b8;
  cursor: pointer;
}
.logout:hover {
  color: #d93026;
}
.content {
  flex: 1;
  overflow: hidden;
}
.content-inner {
  padding: 20px 24px 40px;
  min-height: 100%;
  /* 超宽屏时内容居中收拢，避免表格被拉成一长条、视线来回扫 */
  max-width: 1560px;
  margin: 0 auto;
  /* 页面重建时内容轻轻淡入，遮掉首帧的空表格抖动；
     从 0.35 起而不是 0，避免出现"闪一下全白" */
  animation: content-in 0.14s ease-out both;
}
@keyframes content-in {
  from {
    opacity: 0.55;
    transform: translateY(2px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
