<template>
  <view class="err-page">
    <text class="code">403</text>
    <text class="title">无权访问该功能</text>
    <text class="desc">当前账号（{{ roleName }}）没有该操作权限，如需开通请联系系统管理员。</text>
    <view class="actions">
      <view class="btn plain" @click="goHome">返回工作台</view>
      <view class="btn" @click="back">返回上一页</view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'
import { relaunch, back as goBack } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const store = useUserStore()
const roleName = computed(() => store.roleName || '未登录')

function goHome() {
  relaunch(ROUTES.HOME)
}
function back() {
  goBack()
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
  color: $text-1;
}
.desc {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: $text-3;
  text-align: center;
  line-height: 1.7;
}
.actions {
  margin-top: 56rpx;
  display: flex;
  flex-direction: row;
  gap: 20rpx;
}
.btn {
  padding: 0 44rpx;
  height: 84rpx;
  border-radius: 42rpx;
  background: $brand;
  color: #fff;
  font-size: 28rpx;
  display: flex;
  align-items: center;
}
.btn.plain {
  background: #fff;
  color: $brand;
  border: 1px solid $brand;
}
</style>
