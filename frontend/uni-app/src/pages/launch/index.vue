<template>
  <view class="launch">
    <view class="brand">
      <brand-logo size="lg" tone="light" stack sub="AGRI-ORIGIN · FISCO-BCOS" />
    </view>
    <view class="loading-bar"><view class="inner"></view></view>
  </view>
</template>

<script setup>
import { onLoad } from '@dcloudio/uni-app'
import { getToken } from '@/utils/auth'
import { isPcWidth } from '@/config/platform'
import { ROUTES } from '@/config/routes'
import BrandLogo from '@/components/common/brand-logo.vue'

onLoad(() => {
  const hasToken = !!getToken()
  // 给启动动画留一点时间，避免闪屏
  setTimeout(() => {
    // #ifdef H5
    if (isPcWidth()) {
      return uni.reLaunch({ url: hasToken ? ROUTES.PC_DASHBOARD : ROUTES.LOGIN })
    }
    // #endif
    uni.reLaunch({ url: hasToken ? ROUTES.HOME : ROUTES.LOGIN })
  }, 260)
})
</script>

<style lang="scss" scoped>
.launch {
  width: 100%;
  min-height: 100vh;
  background: $brand-grad;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.loading-bar {
  margin-top: 80rpx;
  width: 240rpx;
  height: 4rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 2rpx;
  overflow: hidden;
}
.inner {
  width: 40%;
  height: 100%;
  background: #fff;
  border-radius: 2rpx;
  animation: slide 1s ease-in-out infinite;
}
@keyframes slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}
</style>
