<template>
  <view v-if="progress && progress.stages && progress.stages.length" class="pc-card wf-card">
    <view class="pc-card-head">
      <text class="pc-card-title">审核进度</text>
      <view class="wf-head-right">
        <text class="wf-pct">{{ progress.percent }}%</text>
        <text class="wf-done">{{ progress.advancedCount || progress.doneCount }}/{{ progress.totalCount }} 环节已推进</text>
      </view>
    </view>
    <view class="pc-card-body">
      <view class="wf-bar"><view class="wf-bar-in" :style="{ width: (progress.percent || 0) + '%' }"></view></view>
      <view class="wf-now" :class="{ warn: isWaiting }">
        <text v-if="waitingTag" class="wf-chip">{{ waitingTag }}</text>
        <text class="wf-tip">{{ progress.tip }}</text>
      </view>

      <view class="wf-strip">
        <view
          v-for="(s, i) in progress.stages"
          :key="s.code"
          class="wf-node"
          :class="s.status.toLowerCase()"
        >
          <view class="wf-track">
            <view class="wf-dot"></view>
            <view v-if="i < progress.stages.length - 1" class="wf-link"></view>
          </view>
          <text class="wf-name">{{ s.title }}</text>
          <text class="wf-state">{{ statusText(s.status) }}</text>
        </view>
      </view>

      <view class="wf-table">
        <view v-for="s in filledStages" :key="'d' + s.code" class="wf-line" :class="s.status.toLowerCase()">
          <text class="wf-t1">{{ s.title }}</text>
          <text class="wf-t2">{{ lineOf(s) }}</text>
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

const isWaiting = computed(() => {
  const p = props.progress
  return !!p && (p.currentStageCode === 'PENDING' || p.tip && p.tip.indexOf('等待') === 0)
})

const waitingTag = computed(() => {
  const p = props.progress
  if (!p) return ''
  if (p.currentStageCode === 'DONE') return '全部完成'
  if (p.waitingRole && p.waitingNames) return `等待 ${p.waitingNames} 审核`
  if (p.waitingRole) return `等待${p.waitingRole}`
  return ''
})

/** 只列有信息的环节，避免整屏"未开始" */
const filledStages = computed(() => {
  const p = props.progress
  if (!p) return []
  return p.stages.filter((s) => s.status !== 'TODO')
})

function statusText(s) {
  return STATUS_TEXT[s] || s
}

function lineOf(s) {
  const parts = []
  if (s.submitBy) parts.push('提交 ' + s.submitBy + (s.submitTime ? ' · ' + s.submitTime : ''))
  if (s.auditBy || s.auditTime) parts.push('审核 ' + (s.auditBy || '-') + (s.auditTime ? ' · ' + s.auditTime : ''))
  if (!parts.length && s.tip) parts.push(s.tip)
  return parts.join('　｜　')
}
</script>

<style lang="scss" scoped>
.wf-card .pc-card-body {
  padding: 14px 18px 16px;
}
.wf-head-right {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: 10px;
}
.wf-pct {
  font-size: 15px;
  font-weight: 700;
  color: #1f7a4d;
}
.wf-done {
  font-size: 11.5px;
  color: #94a3b8;
}
.wf-bar {
  height: 6px;
  background: #eef1f4;
  border-radius: 4px;
  overflow: hidden;
}
.wf-bar-in {
  height: 100%;
  background: linear-gradient(90deg, #2f9e63, #1f7a4d);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.wf-now {
  margin-top: 12px;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 8px;
  background: #f6f9f7;
  border: 1px solid #e3ece6;
  border-radius: 6px;
  padding: 9px 11px;
}
.wf-now.warn {
  background: #fffaf0;
  border-color: #f5e3c2;
}
.wf-chip {
  flex-shrink: 0;
  font-size: 11px;
  color: #b45309;
  background: #fff1d6;
  border-radius: 4px;
  padding: 1px 7px;
  white-space: nowrap;
}
.wf-tip {
  flex: 1;
  font-size: 12.5px;
  color: #475569;
  line-height: 1.6;
}

/* 横向环节条 */
.wf-strip {
  margin-top: 16px;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}
.wf-node {
  flex: 1;
  min-width: 0;
}
.wf-track {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.wf-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #cbd5e1;
  flex-shrink: 0;
}
.wf-link {
  flex: 1;
  height: 2px;
  background: #e5e9ef;
  margin-left: 4px;
}
.wf-name {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.wf-state {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}
.done .wf-dot {
  background: #1f7a4d;
}
.done .wf-name {
  color: #1f7a4d;
}
.done .wf-state {
  color: #4f9c76;
}
.pending .wf-dot {
  background: #e5a13a;
}
.todo .wf-dot {
  background: #cbd5e1;
}
.pending .wf-state {
  color: #b45309;
}
.rejected .wf-dot,
.abnormal .wf-dot {
  background: #d93026;
}
.rejected .wf-state,
.abnormal .wf-state {
  color: #d93026;
}

/* 明细行 */
.wf-table {
  margin-top: 14px;
  border-top: 1px dashed #e8edf2;
  padding-top: 6px;
}
.wf-line {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: 12px;
  padding: 5px 0;
  font-size: 12px;
}
.wf-t1 {
  width: 76px;
  flex-shrink: 0;
  color: #334155;
}
.wf-t2 {
  flex: 1;
  color: #64748b;
  line-height: 1.6;
}
.rejected .wf-t2,
.abnormal .wf-t2 {
  color: #b42318;
}
</style>
