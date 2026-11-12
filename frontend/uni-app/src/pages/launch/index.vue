<template>
  <view class="launch" :class="[plate, { leaving, hop }]">
    <!-- 背景层：底色 → 渐变 → 两团柔光 + 细网格 + 同心弧，纯 CSS，小程序也能画 -->
    <view class="bg">
      <view class="grad"></view>
      <view class="glow g1"></view>
      <view class="glow g2"></view>
      <view class="grid"></view>
      <view class="arc a"></view>
      <view class="arc b"></view>
    </view>

    <!--
      从官网点"进入业务系统"过来：官网的过场已经播过一遍，
      这里只把同一个画面接住（标识 / 名称 / 进度条的位置与尺寸都和官网一致），
      让进度条补完最后一小段就进系统 —— 不再重播第二遍品牌动画。
    -->
    <view v-if="hop" class="relay">
      <image class="relay-mark" src="/static/logo/mark.png" mode="aspectFit" />
      <view class="relay-name">农安链溯源</view>
      <view class="relay-bar"><view class="relay-fill"></view></view>
      <text class="relay-tip">正在进入业务系统</text>
    </view>

    <template v-else>
      <view class="center">
        <!-- 标识：背后两圈涟漪扩散，标识本身从下方浮起 -->
        <view class="mark-wrap">
          <view class="ripple r1"></view>
          <view class="ripple r2"></view>
          <!-- App 专属：光晕 + 一正一反两道弧线，像系统级启动动画 -->
          <view v-if="plate === 'app'" class="app-halo"></view>
          <view v-if="plate === 'app'" class="app-ring"></view>
          <view v-if="plate === 'app'" class="app-ring2"></view>
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

      <view class="foot" :style="{ animationDelay: footDelay + 'ms' }">
        <!-- 进度条按时长走满，比一直转圈的加载条更像"正在开场" -->
        <view class="progress">
          <view
            class="bar"
            :style="{ animationDelay: footDelay + 'ms', animationDuration: barMs + 'ms' }"
          ></view>
        </view>
        <text class="chain">{{ footNote }}</text>
      </view>
    </template>
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

/** 是否"从官网过来"：官网已经播过一次过场，这里只接力，不再重播 */
const hop = ref(false)

/**
 * 开场动画的时长。
 * App 是"点开一瞬间"，比网页短；小程序有微信自己的加载，也偏快；
 * 浏览器端保留完整的品牌叙事——已登录是回访，1.3s；未登录第一次见，1.9s。
 */
const holdMs = ref(1900)
const leaving = ref(false)
let t1 = null
let t2 = null

/** 淡出耗时，与样式里 .launch 的 opacity transition 一致 */
const FADE = 240

/**
 * 走满之后先停一下再淡出。
 * CSS 动画是从元素渲染那帧开始计时，比 onLoad 晚几十毫秒，
 * 不留这点余量的话，条总是差最后一两个百分点就开始透明，
 * 看起来就是"没走完"。80ms 既够补齐误差，又不会让人觉得在等。
 */
const GAP = 80

/**
 * 进度条的入场延迟与走完时长（毫秒）。
 *
 * 以前进度条元素一挂载就开始计时，但它所在的 .foot 要等 0.4~0.95s 才淡入，
 * 结果是"看不见的时候已经走了大半"，等它出现时只剩最后一小截，
 * 看起来就像"条没走完就进主界面"。
 *
 * 现在把三端的时间轴统一在这里算：
 *   · 进度条与 .foot 同时出现（同一个延迟）；
 *   · 走完的时刻正好是开始淡出的时刻，条满 → 淡出 → 进系统。
 */
const footDelay = ref(950)
const barMs = ref(710)

/**
 * @param {number} hold   整段停留时长
 * @param {number} foot   进度条容器（也是进度条本身）的入场延迟
 * @param {number} fixed  指定进度条时长（接力模式用，不参与剩余时间分配）
 */
function plan(hold, foot, fixed) {
  holdMs.value = hold
  let fd = foot
  let bar = fixed || 0
  if (!bar) {
    if (hold - fd - FADE - GAP < 380) fd = Math.max(180, hold - FADE - GAP - 380)
    bar = hold - fd - FADE - GAP
  }
  footDelay.value = fd
  barMs.value = bar
}

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

  /*
   * 从官网点"进入业务系统"过来的：官网那一下已经播完品牌的过场，
   * 这里如果再来一遍（标识浮起 + 标题 + 标语 + 进度条），就是同一段动画演两次，
   * 用户会觉得"加载完了又跳出来一个界面"。
   * 所以接住画面，只把进度条补完最后一小段。标记读完即删，只影响这一次。
   */
  // #ifdef H5
  try {
    if (plate.value !== 'app' && sessionStorage.getItem('agri_portal_hop') === '1') {
      sessionStorage.removeItem('agri_portal_hop')
      hop.value = true
    }
    /*
     * index.html 在解析阶段给 html 打了 agri-boot（只在这一跳生效），
     * 现在启动页已经画出来了，摘掉它，免得后续浅色页面在透明处透出深绿。
     */
    document.documentElement.className = document.documentElement.className
      .replace(/\s*\bagri-boot\b/g, '')
      .trim()
  } catch (e) {}
  // #endif

  if (hop.value) {
    // 官网那边遮罩 620ms 里进度条已经走满，这里只做收尾：补一点点、停一下、淡出
    plan(600, 0, 200)
  } else if (plate.value === 'app') {
    /*
     * App 是"点开一瞬间"，但也不能快到看不清：
     * 之前给的是 1000/1350ms，留出 400ms 的进度条入场延迟后只剩 280~380ms，
     * 条刚露头就开始淡出，观感就是"没走完就进主界面"。
     * 现在给 1700/2000ms，进度条实际有 1.1~1.4s 走完，走满再停 80ms 才淡出。
     */
    plan(hasToken ? 1700 : 2000, 260)
  } else if (plate.value === 'mp') {
    plan(hasToken ? 1000 : 1450, 500)
  } else {
    plan(hasToken ? 1300 : 1900, 950)
  }

  // 先淡出，再跳转：直接 reLaunch 会"啪"地切走，动画等于白做
  t1 = setTimeout(() => {
    leaving.value = true
  }, holdMs.value - FADE)
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
}
/*
  退出时不要让整页淡出。
  根节点一旦变透明，露出来的是页面底色 #f4f6f8 —— 在手机上看就是"快进主界面时闪一下白"，
  也正是"条还没走完就跳走"的观感来源之一。
  现在保留深色底，只让内容（标识/标题/进度条）淡掉，交接瞬间背景仍是同一块深色。
*/
.launch.leaving .center,
.launch.leaving .foot,
.launch.leaving .relay {
  opacity: 0;
  transition: opacity 0.24s ease;
}
.launch.leaving .foot,
.launch.leaving .relay {
  animation: none;
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

/* ---------------- 官网过来的接力画面（仅 H5，结构与官网遮罩一一对应） ---------------- */
/* #ifdef H5 */
.launch.hop .grad {
  background: radial-gradient(120% 100% at 50% 0%, #123528 0%, #0b1e17 58%, #070f0c 100%);
}
/*
  网格与同心弧是"业务系统启动页"才有的装饰。
  接力模式下去掉，画面与官网遮罩逐像素一致 —— 跨页面切换时看不出换了一层。
*/
.launch.hop .grid,
.launch.hop .arc,
.launch.hop .glow {
  display: none;
}
.relay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
/* 以下尺寸 / 间距 / 颜色与官网 .rv-* 完全一致，跨页面切换时标识不会跳动 */
.relay-mark {
  width: clamp(72px, 9vw, 104px);
  height: clamp(72px, 9vw, 104px);
  filter: drop-shadow(0 14px 30px rgba(0, 0, 0, 0.5));
}
.relay-name {
  margin-top: 20px;
  font-size: clamp(15px, 1.5vw, 19px);
  letter-spacing: 0.18em;
  font-weight: 600;
  color: #e8fff3;
}
.relay-bar {
  margin-top: 28px;
  width: clamp(150px, 17vw, 212px);
  height: 3px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.16);
  overflow: hidden;
}
.relay-fill {
  width: 100%;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #45e0a6, #d6ffe9);
  box-shadow: 0 0 12px rgba(69, 224, 166, 0.55);
  transform-origin: left center;
  animation: relayFill 0.2s cubic-bezier(0.4, 0, 0.2, 1) both;
}
.relay-tip {
  margin-top: 16px;
  font-size: clamp(11px, 1vw, 12.5px);
  letter-spacing: 0.16em;
  color: rgba(224, 255, 238, 0.55);
}
/*
  官网那 620ms 已经把条推到满格，这里千万不能从 0 再走一遍（会看到条往回缩）。
  起点只相差 3.5%，肉眼几乎看不出，观感是"条在这儿轻轻一顶就到底了"。
*/
@keyframes relayFill {
  from {
    transform: scaleX(0.965);
  }
  to {
    transform: scaleX(1);
  }
}
@media (prefers-reduced-motion: reduce) {
  .relay-fill {
    animation: none;
    transform: none;
  }
}
/* #endif */

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
  /*
    标识本身只做一次"浮起对焦"，不再持续缩放：
    呼吸感交给背后的光晕，标识保持清晰稳定 ——
    两个都缩放会让人觉得画面在晃。
  */
  animation: markRise 0.58s cubic-bezier(0.22, 0.9, 0.3, 1) both;
}
@keyframes markRise {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.94);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
/*
  App 专属的加载语汇：一团缓慢呼吸的柔光 + 一正一反两道细弧。
  浏览器端继续用扩散涟漪，小程序端有自己的节奏——
  三端各有各的启动感，但标识、配色、文案仍是同一套。
  涟漪在 App 里收掉，避免"扩散 + 旋转"两种动势互相打架。
*/
.launch.app .ripple {
  display: none;
}
.launch.app .app-halo {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 268px;
  height: 268px;
  margin: -134px 0 0 -134px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(69, 224, 166, 0.3) 0%,
    rgba(69, 224, 166, 0.11) 36%,
    rgba(69, 224, 166, 0) 70%
  );
  animation: haloPulse 3.6s ease-in-out both infinite;
}
@keyframes haloPulse {
  0%,
  100% {
    opacity: 0.52;
    transform: scale(0.94);
  }
  50% {
    opacity: 1;
    transform: scale(1.06);
  }
}
.launch.app .app-ring {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 168px;
  height: 168px;
  margin: -84px 0 0 -84px;
  border-radius: 50%;
  border: 1.5px solid rgba(139, 240, 181, 0.12);
  border-top-color: rgba(139, 240, 181, 0.85);
  animation: ringSpin 1.4s linear both infinite;
}
/* 反向的第二道弧，更细更淡：让外圈看起来有"层次"而不是一根线在转 */
.launch.app .app-ring2 {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 206px;
  height: 206px;
  margin: -103px 0 0 -103px;
  border-radius: 50%;
  border: 1px solid rgba(139, 240, 181, 0.08);
  border-bottom-color: rgba(139, 240, 181, 0.46);
  animation: ringSpin 2.8s linear both infinite reverse;
}
@keyframes ringSpin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes markBreath {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.035);
  }
}
.launch.app .title {
  margin-top: 18px;
  /*
    文字用"由虚到实"的对焦感进场，比单纯上浮更像原生启动动画。
    blur 只在 App（WebView）里用，两端都能渲染，不会拖慢首帧。
  */
  animation: titleFocus 0.52s cubic-bezier(0.22, 0.9, 0.3, 1) 0.1s both;
}
@keyframes titleFocus {
  from {
    opacity: 0;
    transform: translateY(8px);
    filter: blur(7px);
  }
  to {
    opacity: 1;
    transform: none;
    filter: blur(0);
  }
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
  .ripple,
  .mark,
  .app-halo,
  .app-ring,
  .app-ring2 {
    animation: none !important;
  }
  .divider {
    transform: none;
  }
  /* 关掉动画后把被 keyframes 的初始态补回来，避免元素停在 opacity:0 */
  .title {
    opacity: 1;
    filter: none;
  }
  .mark {
    opacity: 1;
  }
}
</style>
