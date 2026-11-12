<template>
  <!--
    App 壳内专用底部 Dock（五个入口，中间凸起扫码）。

    为什么要抽成组件：壳内首页 pages/app/home 用的是这块自绘 Dock，
    但点"批次/溯源/我的"会 switchTab 到小程序/浏览器共用的 tabBar 页面，
    那几页挂的是系统 tabBar（四个入口），于是同一个 App 里会出现
    "5 个图标 → 4 个图标"的跳变。这里让四个 tab 页在壳内也渲染同一块 Dock，
    系统 tabBar 由 body.agri-app-shell 的样式统一隐藏，视觉与入口数量就一致了。
  -->
  <view class="ad-wrap">
    <!-- 占位：把页面内容顶到 Dock 上方，最后一张卡不会被遮住 -->
    <view class="ad-space"></view>

    <view class="ad" :class="{ 'ad-in': animate }">
      <view class="ad-item" :class="{ on: homeOn }" @click="toHome">
        <app-icon name="farm" :color="homeOn ? ON : OFF" :size="44" />
        <text class="ad-t">首页</text>
      </view>
      <view class="ad-item" :class="{ on: active === 'batch' }" @click="toBatch">
        <app-icon name="batch" :color="colorOf('batch')" :size="44" />
        <text class="ad-t">批次</text>
      </view>

      <!-- 中间凸起扫码：圆形按钮压出 Dock 顶边，这是 App 最直观的视觉特征 -->
      <view class="ad-scan" @click="toScan">
        <view class="scan-inner">
          <app-icon name="scan" color="#ffffff" :size="50" />
        </view>
        <text class="ad-t scan-t">扫码</text>
      </view>

      <view class="ad-item" :class="{ on: active === second.act }" @click="toSecond">
        <app-icon :name="second.icon" :color="colorOf(second.act)" :size="44" />
        <text class="ad-t">{{ second.text }}</text>
      </view>
      <view class="ad-item" :class="{ on: active === 'mine' }" @click="toMine">
        <app-icon name="user" :color="colorOf('mine')" :size="44" />
        <text class="ad-t">我的</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from '@/components/common/app-icon.vue'
import { useUserStore } from '@/store/user'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const props = defineProps({
  /** 当前所在位置：home / batch / trace / mine */
  active: { type: String, default: '' },
  /** 首页首次进场是否播放上浮动画（只首页用，其余页面直接出现，避免每次切页都"跳一下"） */
  animate: { type: Boolean, default: false }
})

const store = useUserStore()

const OFF = '#374151'
const ON = '#1f7a4d'

function colorOf(key) {
  return props.active === key ? ON : OFF
}

/**
 * 工作台是"首页"下的二级页（首页右上角「工作台 ›」进去的），
 * Dock 里没有它的位置，但也不能一格都不亮 —— 那样看起来像"当前不在任何页"。
 * 归属到首页这一格，符合 iOS/Android 里"二级页沿用父 tab 高亮"的习惯。
 */
const homeOn = computed(() => props.active === 'home' || props.active === 'workbench')

/**
 * 第 4 格：能审核的角色给"待办"，其余给"溯源"。
 * 沿用首页原来的规则，保证不同角色看到的入口不变。
 */
const second = computed(() =>
  store.hasPerm('batch:audit')
    ? { icon: 'bell', text: '待办', act: 'todo' }
    : { icon: 'chain', text: '溯源', act: 'trace' }
)

function toHome() {
  if (props.active === 'home') return uni.pageScrollTo({ scrollTop: 0, duration: 200 })
  // 首页不是 tabBar 页，只能用 reLaunch 回去
  uni.reLaunch({ url: ROUTES.APP_HOME })
}
function toBatch() {
  uni.switchTab({ url: ROUTES.TAB_BATCH })
}
function toScan() {
  // 溯源页自带扫码入口，切过去由它拉起相机，不重复实现一套扫码
  uni.switchTab({ url: ROUTES.TAB_TRACE })
}
function toSecond() {
  if (second.value.act === 'todo') return nav(ROUTES.WORKFLOW_TODO)
  uni.switchTab({ url: ROUTES.TAB_TRACE })
}
function toMine() {
  uni.switchTab({ url: ROUTES.TAB_MINE })
}
</script>

<style lang="scss" scoped>
.ad-space {
  height: calc(150rpx + constant(safe-area-inset-bottom));
  height: calc(150rpx + env(safe-area-inset-bottom));
}

.ad {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  height: calc(116rpx + constant(safe-area-inset-bottom));
  height: calc(116rpx + env(safe-area-inset-bottom));
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);
  background: rgba(255, 255, 255, 0.97);
  border-top: 1px solid #eef2f4;
  box-shadow: 0 -6rpx 24rpx rgba(15, 40, 28, 0.05);
}

.ad-item {
  flex: 1;
  height: 116rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ad-item:active {
  opacity: 0.6;
}
.ad-t {
  margin-top: 6rpx;
  font-size: 18rpx;
  color: $text-3;
}
/* 当前所在页：图标与文字同时转品牌绿，一眼能看出自己在哪一格 */
.ad-item.on .ad-t {
  color: $brand;
  font-weight: 600;
}

.ad-scan {
  flex: 1;
  height: 116rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding-bottom: 10rpx;
  position: relative;
}
.scan-inner {
  position: absolute;
  top: -34rpx;
  width: 108rpx;
  height: 108rpx;
  border-radius: 54rpx;
  background: linear-gradient(140deg, #1f7a4d 0%, #2f9e63 100%);
  border: 6rpx solid #ffffff;
  box-shadow: 0 10rpx 24rpx rgba(31, 122, 77, 0.28);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.12s ease;
}
.ad-scan:active .scan-inner {
  transform: scale(0.94);
}
.scan-t {
  margin-bottom: 2rpx;
}

/* 首页首次进场：Dock 从下方浮起，与内容面板的节奏错开 */
.ad-in {
  animation: adDock 0.5s 0.1s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
@keyframes adDock {
  from {
    opacity: 0;
    transform: translateY(60rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ad-in {
    animation: none;
  }
}
</style>
