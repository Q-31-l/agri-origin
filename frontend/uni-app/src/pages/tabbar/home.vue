<template>
  <view class="home" :class="{ 'as-app': appShell }">
    <view class="header">
      <view class="header-bg"></view>
      <app-navbar transparent>
        <template #title>
          <text class="nav-title">工作台</text>
        </template>
        <template #right>
          <view class="nav-action" @click="toTrace"><app-icon name="scan" color="#1f7a4d" :size="42" /></view>
        </template>
      </app-navbar>

      <view class="greet">
        <view class="col flex1">
          <text class="hello">{{ greeting }}，{{ store.displayName || '用户' }}</text>
          <view class="role-row">
            <text class="role">{{ store.roleName || '-' }}</text>
            <text v-if="store.orgName" class="org">{{ store.orgName }}</text>
          </view>
        </view>
        <view class="avatar" @click="toMine">{{ avatarText }}</view>
      </view>

      <view class="stats">
        <view class="stat-item">
          <text class="sv">{{ stats.totalBatches }}</text>
          <text class="sl">批次总数</text>
        </view>
        <view class="sep"></view>
        <view class="stat-item">
          <text class="sv">{{ stats.abnormalBatches }}</text>
          <text class="sl">异常批次</text>
        </view>
        <view class="sep"></view>
        <view class="stat-item">
          <text class="sv">{{ stats.qualifiedRate }}%</text>
          <text class="sl">合格率</text>
        </view>
      </view>
    </view>

    <view class="body">
      <!-- 待我审核：村管 / 监管登录后第一眼就能看到轮到自己处理的事 -->
      <view v-if="todoTotal > 0" class="todo-banner" @click="toTodo">
        <view class="tb-left">
          <text class="tb-title">有 {{ todoTotal }} 项待你审核</text>
          <text class="tb-sub">{{ todoHint }}</text>
        </view>
        <text class="tb-go">去处理 ›</text>
      </view>

      <view class="quick card">
        <view class="card-title">
          <text>快捷操作</text>
        </view>
        <view class="grid">
          <view v-for="q in quicks" :key="q.title" class="grid-item" @click="go(q)">
            <view class="q-icon" :style="{ background: q.bg }">
              <app-icon :name="q.icon" :color="q.color" :size="44" />
            </view>
            <text class="q-title">{{ q.title }}</text>
          </view>
        </view>
      </view>

      <view class="recent">
        <view class="section-head">
          <text class="section-title">最近批次</text>
          <text class="more" @click="toBatch">全部 ›</text>
        </view>
        <view v-if="loading" class="loading">加载中…</view>
        <block v-else>
          <batch-card v-for="b in recent" :key="b.id" :batch="b" />
          <!-- 空状态按角色给不同引导：只有能建批次的角色才显示"新建批次"，
               否则农户点进去只会撞上后端 403（仅生产主体可创建批次） -->
          <empty-state
            v-if="!recent.length"
            :text="emptyText"
            :action-text="canCreateBatch ? '新建批次' : ''"
            @action="createBatch"
          />
        </block>
      </view>
    </view>

    <!-- 壳内：这一页是"工作台"二级页，Dock 照常给出五个入口，
         「首页」回到 App 专属首页，避免又掉回系统 tabBar 那四格 -->
    <app-dock v-if="appShell" active="workbench" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import AppDock from '@/components/app/app-dock.vue'
import { isAppUI } from '@/config/platform'
import { useHideTabBar } from '@/utils/app-dock'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import AppNavbar from '@/components/common/app-navbar.vue'
import AppIcon from '@/components/common/app-icon.vue'
import BatchCard from '@/components/business/batch-card.vue'
import EmptyState from '@/components/common/empty-state.vue'
import { useUserStore } from '@/store/user'

/** App 版式（壳内 / 手机浏览器 / 小程序）：改用自绘 Dock，原生 tabBar 收起 */
const appShell = isAppUI()
useHideTabBar()
import { batchPage, statsOverview, workflowTodo } from '@/api'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { sys } from '@/config/platform'

const store = useUserStore()
const stats = ref({ totalBatches: 0, abnormalBatches: 0, qualifiedRate: 0 })
const recent = ref([])
const loading = ref(false)
const todoTotal = ref(0)
const todoHint = ref('')

const avatarText = computed(() => (store.displayName || 'U').slice(0, 1))

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const ALL_QUICKS = [
  { title: '审核待办', icon: 'check', bg: '#fff3dd', color: '#b45309', path: ROUTES.WORKFLOW_TODO, perm: 'batch:audit' },
  { title: '新建批次', icon: 'plus', bg: '#e8f5ee', color: '#1f7a4d', path: ROUTES.BATCH_CREATE, perm: 'batch:create' },
  { title: '农事记录', icon: 'planting', bg: '#e8f0fe', color: '#2563eb', path: ROUTES.PLANTING_FORM, perm: 'planting:create' },
  { title: '采收登记', icon: 'harvest', bg: '#fef6e0', color: '#b45309', path: ROUTES.HARVEST_FORM, perm: 'harvest:create' },
  { title: '质检录入', icon: 'quality', bg: '#f1eaff', color: '#7c3aed', path: ROUTES.QUALITY_FORM, perm: 'quality:create' },
  { title: '物流登记', icon: 'logistics', bg: '#e0f7fa', color: '#0891b2', path: ROUTES.LOGISTICS_FORM, perm: 'logistics:create' },
  { title: '销售登记', icon: 'sales', bg: '#fff1e6', color: '#c2410c', path: ROUTES.SALES_FORM, perm: 'sales:create' },
  { title: '异常管控', icon: 'abnormal', bg: '#fdeceb', color: '#d93026', path: ROUTES.ABNORMAL, perm: 'abnormal:list' },
  { title: '扫码溯源', icon: 'scan', bg: '#eaf1ff', color: '#3b82f6', path: ROUTES.TAB_TRACE, tab: true }
]

const quicks = computed(() => ALL_QUICKS.filter((q) => !q.perm || store.hasPerm(q.perm)))

const canCreateBatch = computed(() => store.hasPerm('batch:create'))

/**
 * 空状态的文案要区分两种"没有批次"：
 * 自己没建过（生产主体），和还没被指派（农户）——后者不是"空"，是"等分配"。
 */
const emptyText = computed(() =>
  canCreateBatch.value
    ? '还没有批次数据，先建一个批次开始记录'
    : '还没有被指派批次。村管或生产主体把批次分配给你后，这里会出现农事与采收任务'
)

function go(q) {
  if (q.tab) return uni.switchTab({ url: q.path })
  nav(q.path)
}

function toMine() {
  uni.switchTab({ url: ROUTES.TAB_MINE })
}
function toBatch() {
  uni.switchTab({ url: ROUTES.TAB_BATCH })
}
function toTrace() {
  uni.switchTab({ url: ROUTES.TAB_TRACE })
}
function createBatch() {
  nav(ROUTES.BATCH_CREATE)
}

function toTodo() {
  nav(ROUTES.WORKFLOW_TODO)
}

async function load() {
  loading.value = true
  try {
    const [b, s, t] = await Promise.allSettled([
      batchPage({ page: 1, size: 3 }),
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

onShow(() => load())
onPullDownRefresh(() => load())
</script>

<style lang="scss" scoped>
.home {
  min-height: 100vh;
  background: $bg-page;
}
.header {
  background: $brand-grad;
  padding-bottom: 90rpx;
  position: relative;
  /*
    header-bg 是右上角那团光斑，故意做成 right:-100rpx 探出容器。
    探出去会把整个文档撑宽（手机 390 的屏会变成 442），用户能左右拖，
    底部内容也跟着错位——所以这里把探出的部分裁掉，只留视觉。
  */
  overflow: hidden;
}
.header-bg {
  position: absolute;
  right: -100rpx;
  top: -120rpx;
  width: 420rpx;
  height: 420rpx;
  border-radius: 210rpx;
  background: rgba(255, 255, 255, 0.07);
}
.nav-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #fff;
}
.nav-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  /* 白底 + 墨绿图标：图标是 AI 生成的位图，只有固定几种颜色，
     深色底上用白色图标需要额外导出一套，白底是更省的解法，对比度也够 */
  background: rgba(255, 255, 255, 0.92);
}
.nav-action:active {
  background: #ffffff;
}
.greet {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 32rpx;
  margin-top: 8rpx;
}
.hello {
  font-size: 38rpx;
  font-weight: 700;
  color: #fff;
}
.role-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 10rpx;
  gap: 12rpx;
}
.role {
  font-size: 22rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}
.org {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
}
.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 44rpx;
  background: rgba(255, 255, 255, 0.2);
  border: 2rpx solid rgba(255, 255, 255, 0.45);
  color: #fff;
  font-size: 36rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stats {
  margin: 32rpx 24rpx 0;
  height: 140rpx;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: $radius;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.sv {
  font-size: 44rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}
.sl {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 4rpx;
}
.sep {
  width: 1px;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.24);
}
.body {
  margin-top: -60rpx;
  padding: 0 $gap;
  position: relative;
  z-index: 2;
}
.todo-banner {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  background: #fff8ea;
  border: 1px solid #f3dfb9;
  border-radius: $radius;
  padding: 22rpx 24rpx;
  margin-bottom: $gap-sm;
}
.tb-left {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.tb-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #8a4b08;
}
.tb-sub {
  font-size: 22rpx;
  color: #a9712a;
}
.tb-go {
  font-size: 24rpx;
  color: #b45309;
  font-weight: 600;
}
.quick {
  padding-bottom: 12rpx;
}
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 16rpx;
}
.grid-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 0 18rpx;
}
.q-icon {
  width: 84rpx;
  height: 84rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}
.q-title {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $text-2;
}
.recent {
  margin-top: 32rpx;
}
.section-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.more {
  font-size: 24rpx;
  color: $text-3;
}
.loading {
  text-align: center;
  padding: 40rpx;
  color: $text-3;
  font-size: 24rpx;
}
</style>
