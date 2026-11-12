<template>
  <view class="scan-entry" :class="{ 'as-app': appShell }">
    <view class="hero">
      <view class="frame">
        <view class="corner tl"></view>
        <view class="corner tr"></view>
        <view class="corner bl"></view>
        <view class="corner br"></view>
        <text class="frame-icon">⌗</text>
      </view>
      <text class="title">扫码溯源</text>
      <text class="desc">扫描农产品包装上的二维码，查看从田间到餐桌的完整履历</text>

      <view class="scan-button" @click="onScan">
        <text class="sb-icon">⌗</text>
        <text class="sb-text">开始扫码</text>
      </view>
    </view>

    <view class="manual">
      <text class="manual-title">或手动输入溯源码</text>
      <view class="manual-row">
        <input
          v-model="code"
          class="manual-input"
          :focus="inputFocus"
          placeholder="如 TC20260512001"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="query"
        />
        <view class="manual-btn" @click="query">查询</view>
      </view>
      <text class="hint">溯源码可在产品包装标签或批次详情页找到</text>
    </view>

    <view class="recent">
      <text class="recent-title">最近查询</text>
      <view class="recent-list">
        <view v-for="(c, i) in history" :key="i" class="recent-item" @click="pick(c)">
          <text class="ri-code mono">{{ c }}</text>
          <text class="ri-arrow">›</text>
        </view>
        <empty-state v-if="!history.length" text="暂无查询记录" />
      </view>
    </view>

    <!-- 壳内与首页共用同一块 Dock，系统 tabBar 由全局样式隐藏 -->
    <app-dock v-if="appShell" active="trace" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onUnload } from '@dcloudio/uni-app'
import EmptyState from '@/components/common/empty-state.vue'
import AppDock from '@/components/app/app-dock.vue'
import { isAppUI } from '@/config/platform'
import { useHideTabBar } from '@/utils/app-dock'
import { scan, stopH5Scan, extractTraceCode } from '@/utils/scan'
import { storage } from '@/utils/storage'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { toast } from '@/utils/ui'

/** App 版式（壳内 / 手机浏览器 / 小程序）：改用自绘 Dock，原生 tabBar 收起 */
const appShell = isAppUI()
useHideTabBar()

const code = ref('')
const history = ref([])
const inputFocus = ref(false)

onShow(() => {
  history.value = storage.get('trace_history', []) || []
})

onUnload(() => {
  // #ifdef H5
  stopH5Scan()
  // #endif
})

function remember(c) {
  const list = (storage.get('trace_history', []) || []).filter((x) => x !== c)
  list.unshift(c)
  storage.set('trace_history', list.slice(0, 8))
  history.value = list.slice(0, 8)
}

function pick(c) {
  code.value = c
  query()
}

function query() {
  const c = (code.value || '').trim()
  if (!c) return toast('请输入溯源码')
  remember(c)
  nav(ROUTES.TRACE, { code: c })
}

async function onScan() {
  // scan() 内部已经按「权限被拒 / 无摄像头 / 非 HTTPS」给出对应说明，
  // 这里不再用一句笼统的"请手动输入"盖掉真实原因。
  const raw = await scan({ title: '扫描包装上的溯源码' })
  if (!raw) {
    // 用户在扫码层里选了「手动输入」→ 直接把焦点打过去
    // #ifdef H5
    inputFocus.value = false
    setTimeout(() => (inputFocus.value = true), 60)
    // #endif
    return
  }
  const c = extractTraceCode(raw)
  if (!c) return toast('未识别到有效溯源码')
  remember(c)
  nav(ROUTES.TRACE, { code: c })
}
</script>

<style lang="scss" scoped>
.scan-entry {
  min-height: 100vh;
  background: $bg-page;
}
.hero {
  background: $brand-grad;
  padding: 60rpx 48rpx 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.frame {
  width: 260rpx;
  height: 260rpx;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.corner {
  position: absolute;
  width: 52rpx;
  height: 52rpx;
  border: 6rpx solid rgba(255, 255, 255, 0.9);
}
.tl {
  left: 0;
  top: 0;
  border-right: none;
  border-bottom: none;
  border-top-left-radius: 12rpx;
}
.tr {
  right: 0;
  top: 0;
  border-left: none;
  border-bottom: none;
  border-top-right-radius: 12rpx;
}
.bl {
  left: 0;
  bottom: 0;
  border-right: none;
  border-top: none;
  border-bottom-left-radius: 12rpx;
}
.br {
  right: 0;
  bottom: 0;
  border-left: none;
  border-top: none;
  border-bottom-right-radius: 12rpx;
}
.frame-icon {
  font-size: 96rpx;
  color: rgba(255, 255, 255, 0.28);
}
.title {
  margin-top: 32rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
}
.desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.78);
  text-align: center;
  line-height: 1.6;
}
.scan-button {
  margin-top: 40rpx;
  height: 92rpx;
  padding: 0 64rpx;
  border-radius: 46rpx;
  background: #fff;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.sb-icon {
  font-size: 34rpx;
  color: $brand;
  margin-right: 12rpx;
}
.sb-text {
  font-size: 30rpx;
  font-weight: 500;
  color: $brand;
}
.manual {
  margin: -40rpx $gap 0;
  background: #fff;
  border-radius: $radius;
  padding: 28rpx;
  box-shadow: $shadow-card;
}
.manual-title {
  font-size: 26rpx;
  color: $text-2;
}
.manual-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 16rpx;
  gap: 16rpx;
}
.manual-input {
  flex: 1;
  height: 80rpx;
  background: #f6f8fa;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
}
.manual-btn {
  width: 140rpx;
  height: 80rpx;
  border-radius: 12rpx;
  background: $brand;
  color: #fff;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hint {
  display: block;
  margin-top: 14rpx;
  font-size: 20rpx;
  color: $text-4;
}
.recent {
  padding: 32rpx $gap;
}
.recent-title {
  font-size: 26rpx;
  color: $text-3;
}
.recent-list {
  margin-top: 16rpx;
  background: #fff;
  border-radius: $radius;
  overflow: hidden;
}
.recent-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 24rpx;
  border-bottom: 1px solid $border;
}
.recent-item:last-child {
  border-bottom: none;
}
.ri-code {
  font-size: 26rpx;
  color: $text-1;
}
.ri-arrow {
  font-size: 32rpx;
  color: $text-4;
}
</style>
