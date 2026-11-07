<template>
  <view class="launch" :class="{ leaving }">
    <!-- 背景层：两团柔光 + 细网格 + 同心弧，纯 CSS，小程序也能画 -->
    <view class="bg">
      <view class="glow g1"></view>
      <view class="glow g2"></view>
      <view class="grid"></view>
      <view class="arc a"></view>
      <view class="arc b"></view>
    </view>

    <view class="center">
      <!-- 标识：背后两圈涟漪扩散，标识本身从下方浮起 -->
      <view class="mark-wrap">
        <view class="ripple r1"></view>
        <view class="ripple r2"></view>
        <image class="mark" src="/static/logo/mark.png" mode="aspectFit" />
      </view>

      <view class="title">
        <text class="t1">农安</text>
        <text class="t2">链溯源</text>
      </view>
      <text class="sub">AGRI-ORIGIN · 区块链农产品溯源平台</text>

      <view class="divider"></view>
      <text class="slogan">每一份农产品，都有可信的来路</text>
    </view>

    <view class="foot">
      <!-- 进度条按时长走满，比一直转圈的加载条更像"正在开场" -->
      <view class="progress">
        <view class="bar" :style="{ animationDuration: holdMs + 'ms' }"></view>
      </view>
      <text class="chain">FISCO-BCOS 联盟链 · 全流程上链存证</text>
    </view>
  </view>
</template>

<script setup>
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getToken } from '@/utils/auth'
import { isPcWidth } from '@/config/platform'
import { ROUTES } from '@/config/routes'

/**
 * 开场动画的时长。
 * 已登录的用户是"回访"，看一眼就走，给 1.3s；
 * 未登录的用户第一次见这个系统，留 1.9s 把品牌讲完再进登录页。
 */
const holdMs = ref(1900)
const leaving = ref(false)
let t1 = null
let t2 = null

onLoad(() => {
  const hasToken = !!getToken()
  holdMs.value = hasToken ? 1300 : 1900

  // 先淡出，再跳转：直接 reLaunch 会"啪"地切走，动画等于白做
  t1 = setTimeout(() => {
    leaving.value = true
  }, holdMs.value - 240)
  t2 = setTimeout(() => {
    // #ifdef H5
    if (isPcWidth()) {
      return uni.reLaunch({ url: hasToken ? ROUTES.PC_DASHBOARD : ROUTES.LOGIN })
    }
    // #endif
    uni.reLaunch({ url: hasToken ? ROUTES.HOME : ROUTES.LOGIN })
  }, holdMs.value)
})

onUnload(() => {
  if (t1) clearTimeout(t1)
  if (t2) clearTimeout(t2)
})
</script>

<style lang="scss" scoped>
/*
  这一页用 px 而不是 rpx：它是全屏居中的开场，不是内容页。
  rpx 在 1440 宽的 PC 上会把标识放大到离谱，用 px 才能三端比例一致。
*/
.launch {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(158deg, #072b1c 0%, #0c3f29 46%, #11553a 100%);
  opacity: 1;
  transition: opacity 0.24s ease;
}
.launch.leaving {
  opacity: 0;
}

/* ---------------- 背景 ---------------- */
.bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.glow {
  position: absolute;
  border-radius: 50%;
}
.g1 {
  width: 520px;
  height: 520px;
  left: 50%;
  top: 50%;
  margin: -300px 0 0 -260px;
  background: radial-gradient(circle, rgba(96, 220, 152, 0.22) 0%, rgba(96, 220, 152, 0) 66%);
}
.g2 {
  width: 380px;
  height: 380px;
  right: -160px;
  bottom: -150px;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.14) 0%, rgba(56, 189, 248, 0) 70%);
}
.grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(420px 420px at 50% 46%, #000 0%, transparent 76%);
  -webkit-mask-image: radial-gradient(420px 420px at 50% 46%, #000 0%, transparent 76%);
}
.arc {
  position: absolute;
  left: 50%;
  top: 50%;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.06);
  transform: translate(-50%, -50%);
}
.arc.a {
  width: 460px;
  height: 460px;
}
.arc.b {
  width: 640px;
  height: 640px;
  border-color: rgba(255, 255, 255, 0.035);
}

/* ---------------- 中心 ---------------- */
.center {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}
.mark-wrap {
  position: relative;
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: markIn 0.72s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.mark {
  position: relative;
  z-index: 2;
  width: 88px;
  height: 88px;
  display: block;
  filter: drop-shadow(0 10px 24px rgba(2, 22, 13, 0.45));
}
/* 两圈涟漪：从标识边缘向外扩散，节奏错开，像信号发出去 */
.ripple {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 96px;
  height: 96px;
  margin: -48px 0 0 -48px;
  border-radius: 50%;
  border: 1px solid rgba(139, 240, 181, 0.5);
  opacity: 0;
  animation: ripple 2.6s ease-out infinite;
}
.r1 {
  animation-delay: 0.35s;
}
.r2 {
  animation-delay: 1.15s;
}

.title {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-top: 22px;
  animation: up 0.56s 0.34s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.t1 {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #ffffff;
}
.t2 {
  font-size: 30px;
  font-weight: 300;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.62);
}
.sub {
  margin-top: 10px;
  font-size: 10.5px;
  letter-spacing: 2.2px;
  color: rgba(224, 255, 238, 0.42);
  animation: up 0.56s 0.48s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
/* 分隔线从中间向两侧展开，给"标题 → 标语"一个停顿 */
.divider {
  width: 148px;
  height: 1px;
  margin-top: 26px;
  background: linear-gradient(
    90deg,
    rgba(139, 240, 181, 0) 0%,
    rgba(139, 240, 181, 0.55) 50%,
    rgba(139, 240, 181, 0) 100%
  );
  transform: scaleX(0);
  animation: grow 0.6s 0.62s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.slogan {
  margin-top: 16px;
  font-size: 12.5px;
  letter-spacing: 1.4px;
  color: rgba(234, 255, 243, 0.72);
  animation: up 0.56s 0.78s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}

/* ---------------- 底部 ---------------- */
.foot {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(52px + constant(safe-area-inset-bottom));
  bottom: calc(52px + env(safe-area-inset-bottom));
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: up 0.6s 0.95s cubic-bezier(0.2, 0.8, 0.24, 1) both;
}
.progress {
  width: 120px;
  height: 2px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.14);
  overflow: hidden;
}
.bar {
  width: 100%;
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, rgba(139, 240, 181, 0.75), #b2ffd6);
  transform-origin: left center;
  animation-name: fill;
  animation-timing-function: linear;
  animation-fill-mode: both;
}
.chain {
  margin-top: 14px;
  font-size: 9.5px;
  letter-spacing: 1.1px;
  color: rgba(224, 255, 238, 0.26);
}

/* ---------------- 动画 ---------------- */
@keyframes markIn {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.86);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes ripple {
  0% {
    opacity: 0.55;
    transform: scale(0.94);
  }
  70% {
    opacity: 0;
    transform: scale(1.75);
  }
  100% {
    opacity: 0;
    transform: scale(1.75);
  }
}
@keyframes up {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes grow {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
@keyframes fill {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

/* PC：屏幕大，标识和标题都放大一点，否则居中会显得空 */
@media (min-width: 900px) {
  .mark-wrap {
    width: 128px;
    height: 128px;
  }
  .mark {
    width: 118px;
    height: 118px;
  }
  .ripple {
    width: 128px;
    height: 128px;
    margin: -64px 0 0 -64px;
  }
  .title {
    margin-top: 26px;
  }
  .t1,
  .t2 {
    font-size: 38px;
  }
  .sub {
    margin-top: 12px;
    font-size: 11.5px;
  }
  .divider {
    width: 184px;
    margin-top: 30px;
  }
  .slogan {
    margin-top: 18px;
    font-size: 14px;
  }
  .arc.a {
    width: 560px;
    height: 560px;
  }
  .arc.b {
    width: 780px;
    height: 780px;
  }
  .foot {
    bottom: 48px;
  }
}

/* 系统开了"减少动态效果"就不播进场动画，直接显示终点状态 */
@media (prefers-reduced-motion: reduce) {
  .mark-wrap,
  .title,
  .sub,
  .divider,
  .slogan,
  .foot,
  .ripple {
    animation: none !important;
  }
  .divider {
    transform: none;
  }
}
</style>
