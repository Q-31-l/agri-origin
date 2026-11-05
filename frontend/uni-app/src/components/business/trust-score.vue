<template>
  <view class="trust">
    <view class="t-top">
      <view class="t-num-wrap">
        <text class="t-num">{{ trust.score }}</text>
        <text class="t-unit">分</text>
      </view>
      <view class="t-info">
        <view class="t-badge" :class="'g-' + trust.grade">
          <text class="t-gd">{{ trust.grade }}</text>
          <text class="t-gt">{{ trust.gradeText }}</text>
        </view>
        <text class="t-sum">{{ trust.summary }}</text>
        <text v-if="trust.scope" class="t-scope">{{ trust.scope }}</text>
      </view>
      <text class="t-cap">溯源可信度</text>
    </view>

    <view class="t-dims">
      <view v-for="d in trust.dimensions" :key="d.key" class="dim">
        <view class="dim-top">
          <text class="dim-name">{{ d.name }}</text>
          <text class="dim-val">{{ d.score }}<text class="dim-max">/{{ d.max }}</text></text>
        </view>
        <view class="dim-track">
          <view class="dim-fill" :class="level(d)" :style="{ width: pct(d) + '%' }"></view>
        </view>
        <text v-if="d.note" class="dim-note">{{ d.note }}</text>
      </view>
    </view>

    <view v-if="hasDeductions" class="t-deduct">
      <view class="dd-head" @click="open = !open">
        <text class="dd-title">扣分说明</text>
        <text class="dd-count">{{ trust.deductions.length }} 项</text>
        <text class="dd-arrow" :class="{ up: open }">⌄</text>
      </view>
      <view v-if="open" class="dd-list">
        <text v-for="(x, i) in trust.deductions" :key="i" class="dd-item">· {{ x }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  trust: { type: Object, required: true }
})

const open = ref(false)
const hasDeductions = computed(
  () => props.trust && props.trust.deductions && props.trust.deductions.length > 0
)

function pct(d) {
  if (!d.max) return 0
  return Math.round((d.score / d.max) * 100)
}

function level(d) {
  const p = pct(d)
  if (p >= 90) return 'lv-a'
  if (p >= 60) return 'lv-b'
  if (p >= 30) return 'lv-c'
  return 'lv-d'
}
</script>

<style lang="scss" scoped>
.trust {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx 28rpx 8rpx;
  margin-bottom: 20rpx;
}

/* ---- 头部：大分数 + 等级 ---- */
.t-top {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  padding-bottom: 24rpx;
  border-bottom: 1rpx solid #f1f5f9;
}
.t-num-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-right: 22rpx;
}
.t-num {
  font-size: 76rpx;
  font-weight: 800;
  line-height: 1;
  color: #1f2937;
  letter-spacing: -2rpx;
}
.t-unit {
  font-size: 24rpx;
  color: #94a3b8;
  margin-left: 2rpx;
}
.t-info {
  flex: 1;
  min-width: 0;
  padding-top: 4rpx;
}
.t-badge {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  margin-bottom: 10rpx;
}
.t-gd {
  font-size: 24rpx;
  font-weight: 700;
  margin-right: 6rpx;
}
.t-gt {
  font-size: 22rpx;
}
.g-A {
  background: #e8f7ee;
  color: #1f7a4d;
}
.g-B {
  background: #e7f5f3;
  color: #0f766e;
}
.g-C {
  background: #fff1e6;
  color: #c2410c;
}
.g-D {
  background: #fdeceb;
  color: #d93026;
}
.t-sum {
  display: block;
  font-size: 22rpx;
  color: #64748b;
  line-height: 1.5;
}
.t-scope {
  display: block;
  font-size: 20rpx;
  color: #b6bec9;
  line-height: 1.5;
  margin-top: 4rpx;
}
.t-cap {
  position: absolute;
  right: 0;
  top: 2rpx;
  font-size: 20rpx;
  color: #b6bec9;
  letter-spacing: 1rpx;
}

/* ---- 五维 ---- */
.t-dims {
  padding: 18rpx 0 6rpx;
}
.dim {
  padding: 12rpx 0;
}
.dim-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.dim-name {
  font-size: 23rpx;
  color: #4b5563;
}
.dim-val {
  font-size: 22rpx;
  color: #1f2937;
  font-weight: 600;
}
.dim-max {
  font-size: 20rpx;
  color: #b6bec9;
  font-weight: 400;
}
.dim-track {
  height: 8rpx;
  border-radius: 4rpx;
  background: #f1f5f9;
  overflow: hidden;
}
.dim-fill {
  height: 8rpx;
  border-radius: 4rpx;
  transition: width 0.5s ease;
}
.lv-a {
  background: linear-gradient(90deg, #4ade80, #1f7a4d);
}
.lv-b {
  background: linear-gradient(90deg, #5eead4, #0f766e);
}
.lv-c {
  background: linear-gradient(90deg, #fdba74, #c2410c);
}
.lv-d {
  background: linear-gradient(90deg, #fca5a5, #d93026);
}
.dim-note {
  display: block;
  font-size: 20rpx;
  color: #94a3b8;
  margin-top: 9rpx;
}

/* ---- 扣分说明 ---- */
.t-deduct {
  border-top: 1rpx solid #f1f5f9;
  margin-top: 8rpx;
}
.dd-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 20rpx 0;
}
.dd-title {
  font-size: 22rpx;
  color: #64748b;
}
.dd-count {
  font-size: 20rpx;
  color: #b6bec9;
  margin-left: 8rpx;
  flex: 1;
}
.dd-arrow {
  font-size: 24rpx;
  color: #b6bec9;
  transition: transform 0.2s;
}
.dd-arrow.up {
  transform: rotate(180deg);
}
.dd-list {
  padding-bottom: 20rpx;
}
.dd-item {
  display: block;
  font-size: 21rpx;
  color: #94a3b8;
  line-height: 1.8;
}
</style>
