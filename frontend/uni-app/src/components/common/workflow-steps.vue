<template>
  <view v-if="progress && progress.stages && progress.stages.length" class="wf">
    <view class="wf-card">
      <view class="wf-top">
        <text class="wf-title">审核进度</text>
        <text class="wf-pct">{{ progress.percent }}%</text>
      </view>
      <view class="wf-bar"><view class="wf-bar-in" :style="{ width: (progress.percent || 0) + '%' }"></view></view>
      <view class="wf-now">
        <view v-if="waitingTag" class="wf-chip">{{ waitingTag }}</view>
        <view class="wf-tip">{{ progress.tip || '暂无进度信息' }}</view>
      </view>
    </view>

    <view class="wf-steps">
      <view v-for="(s, i) in progress.stages" :key="s.code" class="wf-step" :class="s.status.toLowerCase()">
        <view class="wf-axis">
          <view class="wf-dot"></view>
          <view v-if="i < progress.stages.length - 1" class="wf-line"></view>
        </view>
        <view class="wf-body">
          <view class="wf-row">
            <text class="wf-st">{{ s.title }}</text>
            <text class="wf-badge">{{ statusText(s.status) }}</text>
          </view>
          <text v-if="s.submitBy || s.submitTime" class="wf-meta">
            提交：{{ s.submitBy || '-' }}<text v-if="s.submitTime"> · {{ s.submitTime }}</text>
          </text>
          <text v-if="s.auditBy || s.auditTime" class="wf-meta">
            审核：{{ s.auditBy || '-' }}<text v-if="s.auditTime"> · {{ s.auditTime }}</text>
          </text>
          <text v-if="s.status === 'REJECTED' && s.auditRemark" class="wf-meta danger">{{ s.auditRemark }}</text>
          <text v-if="s.tip" class="wf-note">{{ s.tip }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  progress: { type: Object, default: null }
})

const STATUS_TEXT = {
  DONE: '已完成',
  PENDING: '待审核',
  REJECTED: '已驳回',
  TODO: '未开始',
  ABNORMAL: '异常'
}

/**
 * 顶部提示条左侧的状态标签：只说"在等审核"，具体等谁由右侧整句讲，
 * 同一句话不重复两遍（原来左边"等待王秀兰审核"、右边又"已提交，等待…审核"）。
 */
const waitingTag = computed(() => {
  const p = props.progress
  if (!p) return ''
  if (p.currentStageCode === 'DONE') return '已完成'
  if (p.stages && p.stages.some((s) => s.status === 'REJECTED')) return '已驳回'
  if (p.stages && p.stages.some((s) => s.status === 'ABNORMAL')) return '异常'
  if (p.tip && p.tip.indexOf('等待') >= 0) return '待审核'
  return ''
})

function statusText(s) {
  return STATUS_TEXT[s] || s
}
</script>

<style lang="scss" scoped>
.wf {
  padding: 0 24rpx;
  margin-top: 20rpx;
}
.wf-card {
  background: #fff;
  border-radius: $radius;
  box-shadow: $shadow-card;
  padding: 24rpx;
}
.wf-top {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
.wf-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-1;
}
.wf-pct {
  font-size: 26rpx;
  font-weight: 600;
  color: $brand;
}
.wf-bar {
  margin-top: 14rpx;
  height: 10rpx;
  background: #eef1f4;
  border-radius: 6rpx;
  overflow: hidden;
}
.wf-bar-in {
  height: 100%;
  background: $brand;
  border-radius: 6rpx;
  transition: width 0.4s ease;
}
.wf-now {
  margin-top: 16rpx;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 12rpx;
}
.wf-chip {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #b45309;
  background: #fff7e6;
  border-radius: 6rpx;
  padding: 2rpx 12rpx;
  line-height: 1.8;
}
.wf-tip {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: $text-2;
  line-height: 1.6;
  word-break: break-all;
}
.wf-steps {
  margin-top: 16rpx;
  background: #fff;
  border-radius: $radius;
  box-shadow: $shadow-card;
  padding: 24rpx 24rpx 8rpx;
}
.wf-step {
  display: flex;
  flex-direction: row;
}
.wf-axis {
  width: 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.wf-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #cbd5e1;
  margin-top: 10rpx;
}
.wf-line {
  flex: 1;
  width: 2rpx;
  background: #e5e9ef;
  margin: 6rpx 0;
}
.wf-body {
  flex: 1;
  padding-bottom: 24rpx;
}
.wf-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
}
.wf-st {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-1;
}
.wf-badge {
  font-size: 20rpx;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  color: #64748b;
  background: #f1f2f4;
  line-height: 1.8;
}
.wf-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $text-3;
}
.wf-note {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $text-4;
  line-height: 1.6;
}

/* 状态配色：完成绿、待审橙、驳回红、异常红、未开始灰 */
.done .wf-dot {
  background: #1f7a4d;
}
.done .wf-badge {
  color: #1f7a4d;
  background: #e8f6ee;
}
.pending .wf-dot {
  background: #e5a13a;
}
.todo .wf-dot {
  background: #cbd5e1;
}
.pending .wf-badge {
  color: #b45309;
  background: #fff7e6;
}
.rejected .wf-dot,
.abnormal .wf-dot {
  background: #d93026;
}
.rejected .wf-badge,
.abnormal .wf-badge {
  color: #d93026;
  background: #fdecea;
}
.wf-meta.danger {
  color: #d93026;
}
</style>
