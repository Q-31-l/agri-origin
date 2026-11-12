<template>
  <!--
    移动端 App 专属首页（壳内专用的落地页，浏览器版仍是 pages/tabbar/home）。
    只在 isAppShell() 为真时由 launch / login 跳进来，H5、小程序都不走这里。
    设计目标：脱离"网页感"——没有系统标题栏，顶部是品牌栏，
    内容装在一块上浮的白色面板里，底部是自绘的 Dock（中间凸起扫码），
    批次用横向滑动卡而不是竖向列表。
  -->
  <view class="ah">
    <!-- ================= 沉浸式头部 ================= -->
    <view class="hero">
      <view class="deco d1"></view>
      <view class="deco d2"></view>
      <view class="deco d3"></view>

      <view class="brand-row">
        <image class="brand-logo" src="/static/logo/mark.png" mode="aspectFit" />
        <view class="brand-text">
          <text class="brand-name">农安链溯源</text>
          <text class="brand-sub">AGRI-ORIGIN</text>
        </view>
        <view class="brand-badge">APP</view>
      </view>

      <view class="greet-row">
        <view class="greet-col">
          <text class="hello">{{ greeting }}，{{ displayName }}</text>
          <view class="meta-row">
            <text class="role">{{ roleName }}</text>
            <text v-if="orgName" class="org">{{ orgName }}</text>
          </view>
        </view>
        <view class="avatar" @click="toMine">
          <text class="avatar-t">{{ avatarText }}</text>
        </view>
      </view>

      <view class="glass">
        <view class="glass-item">
          <text class="gv">{{ stats.totalBatches }}</text>
          <text class="gl">批次总数</text>
        </view>
        <view class="glass-sep"></view>
        <view class="glass-item">
          <text class="gv" :class="{ hot: stats.abnormalBatches > 0 }">{{ stats.abnormalBatches }}</text>
          <text class="gl">异常批次</text>
        </view>
        <view class="glass-sep"></view>
        <view class="glass-item">
          <text class="gv">{{ stats.qualifiedRate }}<text class="gv-unit">%</text></text>
          <text class="gl">合格率</text>
        </view>
      </view>
    </view>

    <!-- ================= 上浮内容面板 ================= -->
    <view class="sheet">
      <!-- 待办：有才显示，没有不占位 -->
      <view v-if="todoTotal > 0" class="todo" @click="toTodo">
        <view class="todo-ic">
          <app-icon name="bell" color="#b45309" :size="44" />
        </view>
        <view class="todo-main">
          <text class="todo-title">{{ todoTotal }} 项待你处理</text>
          <text class="todo-sub">{{ todoHint || '有新的记录等待审核' }}</text>
        </view>
        <text class="todo-go">去处理</text>
      </view>

      <!-- 快捷入口 -->
      <view class="panel">
        <view class="panel-head">
          <text class="panel-title">常用功能</text>
          <text class="panel-more" @click="toWorkbench">工作台 ›</text>
        </view>
        <view class="grid">
          <view v-for="q in quicks" :key="q.title" class="grid-item" @click="go(q)">
            <view class="g-icon" :style="{ background: q.bg }">
              <app-icon :name="q.icon" :color="q.color" :size="46" />
            </view>
            <text class="g-text">{{ q.title }}</text>
          </view>
        </view>
      </view>

      <!-- 最近批次：横向滑动卡 -->
      <view class="bsec">
        <view class="panel-head">
          <text class="panel-title">最近批次</text>
          <text class="panel-more" @click="toBatch">全部 ›</text>
        </view>

        <view v-if="loading" class="hint">正在加载…</view>

        <scroll-view v-else-if="recent.length" class="hscroll" scroll-x :show-scrollbar="false">
          <view class="hrow">
            <view v-for="b in recent" :key="b.id" class="bcard" @click="openBatch(b)">
              <view class="bcard-top">
                <text class="bno">{{ b.batchNo }}</text>
                <status-tag :value="b.status" />
              </view>
              <text class="bname">{{ b.productName || '未命名产品' }}</text>
              <view class="bmeta">
                <text class="bmeta-t">{{ b.farmName || '未关联农场' }}</text>
                <text class="bmeta-t">{{ formatQty(b.quantity, b.unit) }}</text>
              </view>
              <view class="bfoot">
                <text class="btime">{{ formatDate(b.plantingDate) }} 种植</text>
                <text v-if="b.pendingStage" class="bpending">待审 · {{ b.pendingStage }}</text>
                <text v-else-if="b.abnormalFlag" class="bwarn">异常</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <view v-else class="empty">
          <app-icon name="empty" color="#c7cdd6" :size="72" />
          <text class="empty-t">{{ emptyText }}</text>
          <view v-if="canCreateBatch" class="empty-btn" @click="createBatch">新建批次</view>
        </view>
      </view>
    </view>

    <!-- ================= 自绘 Dock ================= -->
    <!-- 与批次 / 溯源 / 我的 页共用同一个组件：四处都是这块五格 Dock，不再出现"5 格变 4 格" -->
    <app-dock active="home" animate />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import AppIcon from '@/components/common/app-icon.vue'
import AppDock from '@/components/app/app-dock.vue'
import StatusTag from '@/components/common/status-tag.vue'
import { useUserStore } from '@/store/user'
import { batchPage, statsOverview, workflowTodo } from '@/api'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { formatDate, formatQty } from '@/utils/format'
import { isAppUI, isPcWidth } from '@/config/platform'
import { getToken } from '@/utils/auth'

const store = useUserStore()

const stats = ref({ totalBatches: 0, abnormalBatches: 0, qualifiedRate: 0 })
const recent = ref([])
const loading = ref(false)
const todoTotal = ref(0)
const todoHint = ref('')

const displayName = computed(() => store.displayName || '用户')
const roleName = computed(() => store.roleName || '-')
const orgName = computed(() => store.orgName || '')
const avatarText = computed(() => displayName.value.slice(0, 1))

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/**
 * 快捷入口沿用工作台那套权限过滤：没有对应 perm 的入口不出现，
 * 避免农户点进只有生产主体能用的建批次页再撞 403。
 */
const ALL_QUICKS = [
  { title: '待办审核', icon: 'check', bg: '#e8f7ee', color: '#16a34a', path: ROUTES.WORKFLOW_TODO, perm: 'batch:audit' },
  { title: '新建批次', icon: 'plus', bg: '#e8f5ee', color: '#1f7a4d', path: ROUTES.BATCH_CREATE, perm: 'batch:create' },
  { title: '农事记录', icon: 'planting', bg: '#e8f0fe', color: '#2563eb', path: ROUTES.PLANTING_FORM, perm: 'planting:create' },
  { title: '采收登记', icon: 'harvest', bg: '#fef6e0', color: '#b45309', path: ROUTES.HARVEST_FORM, perm: 'harvest:create' },
  { title: '质检录入', icon: 'quality', bg: '#f1eaff', color: '#7c3aed', path: ROUTES.QUALITY_FORM, perm: 'quality:create' },
  { title: '物流登记', icon: 'logistics', bg: '#e0f7fa', color: '#0891b2', path: ROUTES.LOGISTICS_FORM, perm: 'logistics:create' },
  { title: '销售登记', icon: 'sales', bg: '#fff1e6', color: '#c2410c', path: ROUTES.SALES_FORM, perm: 'sales:create' },
  { title: '异常管控', icon: 'abnormal', bg: '#fdeceb', color: '#d93026', path: ROUTES.ABNORMAL, perm: 'abnormal:list' }
]

const quicks = computed(() => ALL_QUICKS.filter((q) => !q.perm || store.hasPerm(q.perm)))
const canCreateBatch = computed(() => store.hasPerm('batch:create'))

const emptyText = computed(() =>
  canCreateBatch.value
    ? '还没有批次，先建一个开始记录'
    : '暂未分配到批次，村管分配后会出现在这里'
)

/* ---------------- 页面守卫 ---------------- */
onLoad(() => {
  /*
   * 这一页是壳内专页。若被浏览器直接访问（分享链接、刷新），
   * 宽屏直接回 PC 看板，窄屏回浏览器版首页，避免出现"半成品 App 页"。
   */
  if (!isAppUI()) {
    // #ifdef H5
    const hasToken = !!getToken()
    if (isPcWidth()) {
      uni.reLaunch({ url: hasToken ? ROUTES.PC_DASHBOARD : ROUTES.LOGIN })
      return
    }
    uni.reLaunch({ url: hasToken ? ROUTES.HOME : ROUTES.LOGIN })
    // #endif
  }
})

onShow(() => load())
onPullDownRefresh(() => load())

/* ---------------- 数据 ---------------- */
async function load() {
  loading.value = true
  try {
    const [b, s, t] = await Promise.allSettled([
      batchPage({ page: 1, size: 6 }),
      statsOverview(),
      workflowTodo()
    ])
    if (b.status === 'fulfilled') recent.value = (b.value.data && b.value.data.records) || []
    if (s.status === 'fulfilled' && s.value.data) stats.value = s.value.data
    if (t.status === 'fulfilled') {
      const d = (t.value && t.value.data) || {}
      todoTotal.value = d.total || 0
      const first = (d.groups || [])[0]
      todoHint.value = first ? first.title + ' ' + first.count + ' 条待处理' : ''
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

/* ---------------- 交互 ---------------- */
function go(q) {
  if (q.tab) return uni.switchTab({ url: q.path })
  nav(q.path)
}
function toBatch() {
  uni.switchTab({ url: ROUTES.TAB_BATCH })
}
function toMine() {
  uni.switchTab({ url: ROUTES.TAB_MINE })
}
function toScan() {
  // 溯源页自带扫码入口，切过去由它拉起相机，不重复实现一套扫码
  uni.switchTab({ url: ROUTES.TAB_TRACE })
}
function toTodo() {
  nav(ROUTES.WORKFLOW_TODO)
}
function toWorkbench() {
  // 「工作台」保留原页面：字段更全的统计与全部入口都在那儿
  uni.switchTab({ url: ROUTES.HOME })
}
function createBatch() {
  nav(ROUTES.BATCH_CREATE)
}
function openBatch(b) {
  nav(ROUTES.BATCH_DETAIL, { id: b.id })
}
</script>

<style lang="scss" scoped>
.ah {
  min-height: 100vh;
  background: #f4f6f8;
}

/* ================= 头部 ================= */
.hero {
  position: relative;
  padding: 28rpx 32rpx 148rpx;
  background: linear-gradient(158deg, #0b3d26 0%, #14563a 48%, #1f7a4d 100%);
  overflow: hidden;
}
.deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.d1 {
  width: 460rpx;
  height: 460rpx;
  right: -160rpx;
  top: -200rpx;
  background: rgba(255, 255, 255, 0.08);
}
.d2 {
  width: 300rpx;
  height: 300rpx;
  left: -120rpx;
  bottom: -110rpx;
  background: rgba(96, 220, 152, 0.14);
}
.d3 {
  width: 200rpx;
  height: 200rpx;
  right: 80rpx;
  top: 60rpx;
  border: 1px solid rgba(255, 255, 255, 0.09);
  background: transparent;
}

.brand-row {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.brand-logo {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
}
.brand-text {
  display: flex;
  flex-direction: column;
  margin-left: 16rpx;
}
.brand-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 1rpx;
}
.brand-sub {
  font-size: 16rpx;
  color: rgba(255, 255, 255, 0.42);
  letter-spacing: 3rpx;
  margin-top: 2rpx;
}
.brand-badge {
  margin-left: 14rpx;
  font-size: 16rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: #0b3d26;
  background: rgba(178, 255, 214, 0.9);
  border-radius: 8rpx;
  padding: 3rpx 10rpx;
}

.greet-row {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 40rpx;
}
.greet-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.hello {
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.25;
}
.meta-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 12rpx;
}
.role {
  font-size: 20rpx;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20rpx;
  padding: 4rpx 16rpx;
}
.org {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.72);
  margin-left: 12rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.avatar {
  width: 92rpx;
  height: 92rpx;
  border-radius: 46rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 2rpx solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 20rpx;
}
.avatar-t {
  font-size: 36rpx;
  font-weight: 600;
  color: #ffffff;
}

/* 玻璃统计条 */
.glass {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 36rpx;
  padding: 26rpx 0;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.16);
}
.glass-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.gv {
  font-size: 46rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.15;
}
.gv.hot {
  color: #ffd9a8;
}
.gv-unit {
  font-size: 24rpx;
  font-weight: 600;
}
.gl {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 8rpx;
}
.glass-sep {
  width: 1px;
  height: 52rpx;
  background: rgba(255, 255, 255, 0.18);
}

/* ================= 上浮面板 ================= */
.sheet {
  position: relative;
  z-index: 3;
  margin-top: -96rpx;
  border-radius: 44rpx 44rpx 0 0;
  background: #ffffff;
  padding: 32rpx 32rpx 24rpx;
  min-height: 60vh;
}

.todo {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 24rpx;
  border-radius: 28rpx;
  background: linear-gradient(120deg, #fff8ea 0%, #fff3dd 100%);
  border: 1px solid #f6e3bd;
}
.todo-ic {
  width: 76rpx;
  height: 76rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
}
.todo-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 20rpx;
  min-width: 0;
}
.todo-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #8a4b08;
}
.todo-sub {
  font-size: 22rpx;
  color: #a9712a;
  margin-top: 4rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.todo-go {
  font-size: 24rpx;
  font-weight: 600;
  color: #b45309;
  padding-left: 12rpx;
}

.panel {
  margin-top: 36rpx;
}
.panel-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.panel-more {
  font-size: 24rpx;
  color: $text-3;
}

.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 20rpx;
}
.grid-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 0 20rpx;
}
.g-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.grid-item:active .g-icon {
  transform: scale(0.94);
}
.g-icon {
  transition: transform 0.12s ease;
}
.g-text {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $text-2;
}

/* ================= 批次横滑 ================= */
.bsec {
  margin-top: 20rpx;
}
.hscroll {
  width: 100%;
  white-space: nowrap;
  margin-top: 20rpx;
}
.hrow {
  display: flex;
  flex-direction: row;
  /* 左右各留一点内边距，卡片滑到边缘时不会贴死屏边 */
  padding: 4rpx 0 8rpx;
}
.bcard {
  flex: 0 0 auto;
  width: 500rpx;
  margin-right: 20rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: #f8fafb;
  border: 1px solid #edf1f4;
}
.bcard:active {
  background: #f2f6f8;
}
.bcard-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.bno {
  font-size: 22rpx;
  color: $text-3;
  letter-spacing: 0.5rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 300rpx;
}
.bname {
  display: block;
  margin-top: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bmeta {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 10rpx;
}
.bmeta-t {
  font-size: 22rpx;
  color: $text-3;
  margin-right: 20rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bfoot {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1px solid #edf1f4;
}
.btime {
  font-size: 20rpx;
  color: $text-4;
}
.bpending {
  font-size: 20rpx;
  color: #b45309;
  background: #fff4e0;
  border-radius: 8rpx;
  padding: 3rpx 10rpx;
}
.bwarn {
  font-size: 20rpx;
  color: #d93026;
  background: #fdeceb;
  border-radius: 8rpx;
  padding: 3rpx 10rpx;
}

.hint {
  padding: 40rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $text-3;
}
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 32rpx 40rpx;
}
.empty-t {
  margin-top: 20rpx;
  font-size: 24rpx;
  color: $text-3;
  text-align: center;
  line-height: 1.6;
}
.empty-btn {
  margin-top: 28rpx;
  padding: 16rpx 48rpx;
  border-radius: 40rpx;
  background: $brand;
  color: #ffffff;
  font-size: 26rpx;
}

/* ================= Dock ================= */

/* ================= 进场 ================= */
/*
  首屏一次性进场：只动 opacity 与 transform，不改布局、不拦点击。
  顺序按视线落点铺开——品牌 → 问候 → 统计 → 待办 → 宫格 → 批次 → Dock，
  看起来是"一层层亮起来"，而不是整页闪一下。
  数据回来后才插入的节点（待办、宫格项）浏览器会重新播一次，天然错峰。
*/
@keyframes ahIn {
  from {
    opacity: 0;
    transform: translateY(16rpx);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.brand-row {
  animation: ahIn 0.46s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.greet-row {
  animation: ahIn 0.46s 0.06s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.glass {
  animation: ahIn 0.5s 0.12s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.sheet .todo {
  animation: ahIn 0.46s 0.16s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.sheet .panel {
  animation: ahIn 0.5s 0.2s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.sheet .bsec {
  animation: ahIn 0.5s 0.26s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.grid-item {
  animation: ahIn 0.4s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
/* 前四格逐格错开；第五格起不再叠加延迟，避免入口等太久才可点 */
.grid-item:nth-child(1) { animation-delay: 0.32s; }
.grid-item:nth-child(2) { animation-delay: 0.36s; }
.grid-item:nth-child(3) { animation-delay: 0.4s; }
.grid-item:nth-child(4) { animation-delay: 0.44s; }
.grid-item:nth-child(n + 5) { animation-delay: 0.48s; }
/* Dock 从底部托上来，落点是最终位置，不占额外高度 */

/* 系统开了"减少动态效果"：直接给终点状态 */
@media (prefers-reduced-motion: reduce) {
  .brand-row,
  .greet-row,
  .glass,
  .sheet .todo,
  .sheet .panel,
  .sheet .bsec,
  .grid-item {
    animation: none !important;
  }
}
</style>
