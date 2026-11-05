<template>
  <view class="brand-logo" :class="[`sz-${size}`, tone, { stack, iconOnly }]">
    <!-- 品牌标识用矢量源导出的 PNG：三端渲染一致，不依赖内联 SVG 支持 -->
    <image class="bl-mark" :src="MARK" mode="aspectFit" />

    <view v-if="!iconOnly" class="bl-text">
      <view class="bl-title">
        <text class="bl-t1">{{ title }}</text>
        <text class="bl-t2">{{ suffix }}</text>
      </view>
      <text v-if="sub" class="bl-sub">{{ sub }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 农安链溯源 · 品牌标识
 *
 * 图形语义：圆角徽章 + 草坡 + 破土而出的双叶嫩芽 + 一枚链上节点，
 * 把「农业（芽 / 土地）」与「区块链（节点）」压进同一个标识里。
 * 图形源文件：src/static/logo/mark.svg
 * 改图后执行 node scripts/gen-logo-png.js 重新导出 PNG。
 */
const MARK = '/static/logo/mark.png'

defineProps({
  /** sm 侧栏 / 导航栏 ｜ md 登录页 ｜ lg 启动页 / 关于页 */
  size: { type: String, default: 'md' },
  /** light: 深色背景上的浅色文字 ｜ dark: 浅色背景上的深色文字 */
  tone: { type: String, default: 'light' },
  iconOnly: { type: Boolean, default: false },
  /** 徽章在上、文字在下（启动页 / 关于页等居中场景） */
  stack: { type: Boolean, default: false },
  title: { type: String, default: '农安' },
  suffix: { type: String, default: '链溯源' },
  sub: { type: String, default: 'AGRI-ORIGIN' }
})
</script>

<style lang="scss" scoped>
.brand-logo {
  display: flex;
  flex-direction: row;
  align-items: center;
}

/* ---- 尺寸档位：内部几何全部按 --m 等比推导，改一处即整体缩放 ---- */
.sz-sm {
  --m: 34px;
  --gap: 9px;
  --t1: 15px;
  --t2: 15px;
  --sub: 8px;
}
.sz-md {
  --m: 46px;
  --gap: 12px;
  --t1: 19px;
  --t2: 19px;
  --sub: 9px;
}
.sz-lg {
  --m: 64px;
  --gap: 16px;
  --t1: 26px;
  --t2: 26px;
  --sub: 11px;
}

.bl-mark {
  width: var(--m);
  height: var(--m);
  flex-shrink: 0;
  display: block;
  border-radius: calc(var(--m) * 0.3);
  box-shadow: 0 3px 10px rgba(4, 32, 18, 0.26);
}

/* 竖排：徽章居中，文字在下方居中 */
.brand-logo.stack {
  flex-direction: column;
  align-items: center;
}
.stack .bl-text {
  margin-left: 0;
  margin-top: calc(var(--m) * 0.2);
  align-items: center;
}
.stack .bl-title {
  justify-content: center;
}
.stack .bl-sub {
  margin-top: 4px;
}

/* ---- 文字 ---- */
.bl-text {
  margin-left: var(--gap);
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.bl-title {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.bl-t1,
.bl-t2 {
  font-size: var(--t1);
  line-height: 1.15;
  letter-spacing: 0.5px;
}
.bl-t1 {
  font-weight: 800;
}
.bl-t2 {
  font-weight: 400;
}
.bl-sub {
  font-size: var(--sub);
  letter-spacing: 1.6px;
  line-height: 1.4;
  margin-top: 1px;
}

/* 深色底 */
.light .bl-t1 {
  color: #ffffff;
}
.light .bl-t2 {
  color: rgba(255, 255, 255, 0.6);
}
.light .bl-sub {
  color: rgba(255, 255, 255, 0.36);
}

/* 浅色底 */
.dark .bl-t1 {
  color: #0f3522;
}
.dark .bl-t2 {
  color: #4b7a61;
}
.dark .bl-sub {
  color: #9db4a9;
}
</style>
