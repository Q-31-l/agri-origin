<template>
  <view class="launch" :class="[plate, { leaving }]">
    <!-- 背景层：底色 → 渐变 → 两团柔光 + 细网格 + 同心弧，纯 CSS，小程序也能画 -->
    <view class="bg">
      <view class="grad"></view>
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
      <text class="chain">{{ footNote }}</text>
    </view>
  </view>
</template>

<script setup>
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { ref, computed } from 'vue'
import { getToken } from '@/utils/auth'
import { isAppShell, isPcWidth } from '@/config/platform'
import { ROUTES } from '@/config/routes'

/**
 * 端标识，只用来切换视觉与节奏，不参与任何跳转判断。
 * web：浏览器（手机/电脑同一套）；app：安装包壳内；mp：微信小程序
 */
const plate = ref('web')

/**
 * 开场动画的时长。
 * App 是"点开一瞬间"，比网页短；小程序有微信自己的加载，也偏快；
 * 浏览器端保留完整的品牌叙事——已登录是回访，1.3s；未登录第一次见，1.9s。
 */
const holdMs = ref(1900)
const leaving = ref(false)
let t1 = null
let t2 = null

/** 底部那行小字，三端各说各的：App 说"进入应用"，小程序说"无需安装" */
const footNote = computed(() => {
  if (plate.value === 'app') return '正在进入应用 · AGRI-ORIGIN'
  if (plate.value === 'mp') return '微信小程序 · 无需下载安装'
  return 'FISCO-BCOS 联盟链 · 全流程上链存证'
})

onLoad(() => {
  const hasToken = !!getToken()

  // #ifdef MP-WEIXIN
  plate.value = 'mp'
  // #endif
  // #ifdef H5
  if (isAppShell()) plate.value = 'app'
  // #endif

  if (plate.value === 'app') {
    holdMs.value = hasToken ? 900 : 1200
  } else if (plate.value === 'mp') {
    holdMs.value = hasToken ? 1000 : 1450
  } else {
    holdMs.value = hasToken ? 1300 : 1900
    // #ifdef H5
    /*
     * 从官网点"进入业务系统"过来的，入口已经播过一次过场（logo + 进度条），
     * 这里再完整讲一遍品牌会让人觉得"卡住了"，所以缩短一档。
     * 标记读完即删，避免影响后续正常打开。
     */
    try {
      if (sessionStorage.getItem('agri_portal_hop') === '1') {
        sessionStorage.removeItem('agri_portal_hop')
        holdMs.value = hasToken ? 820 : 1080
      }
    } catch (e) {}
    // #endif
  }

  // 先淡出，再跳转：直接 reLaunch 会"啪"地切走，动画等于白做
  t1 = setTimeout(() => {
    leaving.value = true
  }, holdMs.value - 240)
  t2 = setTimeout(() => {
    // #ifdef H5
    /*
     * 壳内 App 走专属首页/登录页；浏览器（含 PC 宽屏）与小程序完全不变。
     * 判断放在宽屏判断之前：App 一定是手机宽度，先分流可以少算一次窗口尺寸。
     */
    if (isAppShell()) {
      return uni.reLaunch({ url: hasToken ? ROUTES.APP_HOME : ROUTES.APP_LOGIN })
    }
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
  /*
    首帧底色取原生启动图的品牌深绿（#0B3D26），渐变由 .grad 单独一层淡入。
    App 的启动图是纯色 + 居中标识，如果这里首帧直接铺渐变，点开瞬间会闪一下色差。
  */
  background: #0b3d26;
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
.grad {
  position: absolute;
  inset: 0;
  background: linear-gradient(158deg, #072b1c 0%, #0c3f29 46%, #11553a 100%);
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
/* 渐变层淡入：首帧先给纯色，与原生启动图接上 */
@keyframes gradIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ---------------- 三端各自的节奏 ---------------- */

/*
  App：原生启动图里已经有一个居中的标识，这里把尺寸与位置对齐，
  并且不做位移与缩放 —— 用户看到的是"标识没动，周围亮了起来"。
  这是安装包该有的启动感：不是网页重新加载一次，而是同一个画面接着往下走。
*/
.launch.app .grad {
  animation: gradIn 0.6s ease both;
}
/*
  启动图里标识是"屏幕正中"，这里也必须让标识的中点落在中线，
  不能沿用"整个内容块居中"——内容块下方还有标题与标语，
  整体居中会把标识顶到中线上方八十多像素，从启动图切过来时明显往上跳一截。
  120px 的标识，所以中线减去一半高度。
*/
.launch.app {
  justify-content: flex-start;
  padding-top: calc(50vh - 60px);
}
/*
  启动图里标识是"屏幕正中"，这里也必须让标识的中点落在中线，
  不能沿用"整个内容块居中"——内容块下方还有标题与标语，
  整体居中会把标识顶到中线上方八十多像素，从启动图切过来时明显往上跳一截。
  120px 的标识，所以中线减去一半高度。
*/
.launch.app {
  justify-content: flex-start;
  padding-top: calc(50vh - 60px);
}
.launch.app .mark-wrap {
  width: 120px;
  height: 120px;
  animation: none;
}
.launch.app .mark {
  width: 120px;
  height: 120px;
  /* 启动图里没有投影，这里也去掉，避免同一枚标识在两层之间"跳"一下 */
  filter: none;
}
.launch.app .ripple {
  width: 120px;
  height: 120px;
  margin: -60px 0 0 -60px;
}
.launch.app .title {
  margin-top: 18px;
  animation-duration: 0.44s;
  animation-delay: 0.06s;
}
.launch.app .sub {
  animation-duration: 0.44s;
  animation-delay: 0.14s;
}
.launch.app .divider {
  animation-duration: 0.46s;
  animation-delay: 0.22s;
}
.launch.app .slogan {
  animation-duration: 0.44s;
  animation-delay: 0.3s;
}
.launch.app .foot {
  animation-duration: 0.46s;
  animation-delay: 0.4s;
}

/* 小程序：微信自带加载与右上角胶囊，内容略上移，节奏比浏览器紧一档 */
.launch.mp .center {
  transform: translateY(-14px);
}
.launch.mp .mark-wrap {
  width: 82px;
  height: 82px;
}
.launch.mp .mark {
  width: 76px;
  height: 76px;
}
.launch.mp .ripple {
  width: 82px;
  height: 82px;
  margin: -41px 0 0 -41px;
}
.launch.mp .title {
  margin-top: 18px;
  animation-duration: 0.46s;
  animation-delay: 0.16s;
}
.launch.mp .sub {
  animation-duration: 0.46s;
  animation-delay: 0.24s;
}
.launch.mp .divider {
  animation-duration: 0.5s;
  animation-delay: 0.32s;
}
.launch.mp .slogan {
  animation-duration: 0.46s;
  animation-delay: 0.4s;
}
.launch.mp .foot {
  animation-delay: 0.5s;
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
