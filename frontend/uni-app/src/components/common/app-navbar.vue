<template>
  <view class="navbar" :style="wrapStyle">
    <view class="status" :style="{ height: statusBarHeight + 'px' }"></view>
    <view class="bar">
      <view class="left" @click="onBack">
        <text v-if="showBack" class="back">‹</text>
      </view>
      <view class="title">
        <slot name="title">
          <text class="title-text">{{ title }}</text>
        </slot>
      </view>
      <view class="right">
        <slot name="right"></slot>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { statusBarHeight as getStatusBar } from '@/config/platform'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: false },
  /** 透明背景（用于沉浸式头部） */
  transparent: { type: Boolean, default: false },
  bg: { type: String, default: '' }
})

const statusBarHeight = getStatusBar()

const wrapStyle = computed(() => {
  if (props.transparent) return 'background: transparent;'
  const color = props.bg || '#1f7a4d'
  return `background: ${color};`
})

function onBack() {
  if (!props.showBack) return
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack({ delta: 1 })
  else uni.reLaunch({ url: '/pages/tabbar/home' })
}
</script>

<style lang="scss" scoped>
.navbar {
  width: 100%;
  color: #fff;
}
.bar {
  height: 44px;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 12px;
}
.left {
  width: 48px;
  display: flex;
  align-items: center;
}
.back {
  font-size: 32px;
  line-height: 1;
  margin-top: -4px;
}
.title {
  flex: 1;
  text-align: center;
  overflow: hidden;
}
.title-text {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
}
.right {
  width: 48px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
</style>
