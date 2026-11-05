<template>
  <view class="tl">
    <view v-if="!items.length" class="pc-none">暂无流程记录</view>
    <view v-for="(it, i) in items" :key="i" class="tl-row">
      <text class="tl-time">{{ it.time || '-' }}</text>
      <view class="tl-axis">
        <view class="tl-dot" :style="{ background: dotColor(it) }"></view>
        <view v-if="i < items.length - 1" class="tl-line"></view>
      </view>
      <view class="tl-body">
        <view class="tl-top">
          <text class="tl-title">{{ it.title }}</text>
          <pc-status v-if="it.chainStatus" :value="it.chainStatus" type="chain" />
        </view>
        <view class="tl-meta">
          <text v-if="it.operator" class="m">{{ it.operator }}</text>
          <text v-if="it.detail" class="m">{{ it.detail }}</text>
        </view>
        <view v-if="it.txHash" class="tl-hash">
          <text class="hk">tx</text>
          <text class="hv">{{ shortHash(it.txHash) }}</text>
          <text v-if="it.blockNumber" class="hb">区块 #{{ it.blockNumber }}</text>
        </view>
        <evidence-wall
          v-if="it.evidences && it.evidences.length"
          :items="it.evidences"
          :chained="!!it.txHash"
          compact />
      </view>
    </view>
  </view>
</template>

<script setup>
import PcStatus from '@/components/pc/pc-status.vue'
import EvidenceWall from '@/components/business/evidence-wall.vue'
import { shortHash } from '@/utils/format'

defineProps({
  items: { type: Array, default: () => [] }
})

const COLORS = {
  BATCH: '#1f7a4d',
  PLANTING: '#2563eb',
  HARVEST: '#b45309',
  QUALITY: '#7c3aed',
  LOGISTICS: '#0891b2',
  SALES: '#c2410c',
  ABNORMAL: '#d93026',
  DISPOSAL: '#d93026',
  CORRECTION: '#64748b',
  CLOSE: '#6b7280'
}

function dotColor(it) {
  return COLORS[it.type] || '#94a3b8'
}
</script>

<style lang="scss" scoped>
.tl {
  display: flex;
  flex-direction: column;
}
.tl-row {
  display: flex;
  flex-direction: row;
  align-items: stretch;
}
.tl-time {
  width: 118px;
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
  padding-top: 10px;
  line-height: 1.5;
}
.tl-axis {
  width: 20px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 13px;
}
.tl-dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.tl-line {
  flex: 1;
  width: 1px;
  background: #e8edf3;
  margin: 4px 0 0;
  min-height: 18px;
}
.tl-body {
  flex: 1;
  min-width: 0;
  padding: 8px 0 16px 10px;
  border-bottom: 1px solid #f4f7fa;
}
.tl-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.tl-title {
  font-size: 12.5px;
  font-weight: 500;
  color: #1f2937;
}
.tl-meta {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}
.m {
  font-size: 11px;
  color: #94a3b8;
}
.tl-hash {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
}
.hk {
  font-size: 9px;
  color: #1f7a4d;
  background: #e8f5ee;
  border-radius: 3px;
  padding: 0 4px;
}
.hv {
  font-size: 10.5px;
  color: #64748b;
  font-family: Consolas, monospace;
}
.hb {
  font-size: 10.5px;
  color: #b6bec9;
}
</style>
