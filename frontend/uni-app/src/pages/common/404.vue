<template>
  <view class="err-page">
    <text class="code">404</text>
    <text class="title">页面不存在</text>
    <text class="desc">你访问的页面可能已被移除，或链接地址有误。</text>
    <view class="actions">
      <view class="btn" @click="goHome">返回工作台</view>
    </view>
  </view>
</template>

<script setup>
import { relaunch } from '@/utils/router'
import { ROUTES } from '@/config/routes'

function goHome() {
  relaunch(ROUTES.HOME)
}
</script>

<style lang="scss" scoped>
.err-page {
  min-height: 100vh;
  background: $bg-page;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 60rpx;
}
.code {
  font-size: 140rpx;
  font-weight: 800;
  color: #dbe3ea;
  line-height: 1;
}
.title {
  margin-top: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
}
.desc {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: $text-3;
  text-align: center;
}
.actions {
  margin-top: 56rpx;
}
.btn {
  padding: 0 56rpx;
  height: 84rpx;
  border-radius: 42rpx;
  background: $brand;
  color: #fff;
  font-size: 28rpx;
  display: flex;
  align-items: center;
}
</style>
