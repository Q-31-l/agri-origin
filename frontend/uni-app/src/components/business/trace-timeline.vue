<template>
  <view class="timeline">
    <view v-for="(it, i) in items" :key="i" class="node" :class="{ last: i === items.length - 1 }">
      <view class="axis">
        <view class="dot" :style="{ background: dotColor(it) }"></view>
        <view v-if="i !== items.length - 1" class="line"></view>
      </view>

      <view class="content">
        <view class="head">
          <text class="title">{{ it.title || typeName(it.type) }}</text>
          <text class="time">{{ formatDateTime(it.time) }}</text>
        </view>

        <view v-if="it.operator" class="row">
          <text class="k">操作人</text>
          <text class="v">{{ it.operator }}</text>
        </view>

        <view v-if="it.detail" class="detail">{{ it.detail }}</view>

        <!-- 实证材料：图片可放大，视频可现场播放，哈希随链上摘要可核 -->
        <evidence-wall
          v-if="it.evidences && it.evidences.length"
          :items="it.evidences"
          :chained="!!it.txHash" />

        <view v-if="it.status" class="row">
          <text class="k">状态</text>
          <text class="v" :class="statusClass(it.status)">{{ it.status }}</text>
        </view>

        <view class="chain">
          <text class="k">存证</text>
          <chain-badge :status="it.chainStatus" :block-number="it.blockNumber" />
        </view>

        <view v-if="it.txHash" class="hash">
          <text class="k">tx</text>
          <text class="hash-val mono" @click="copy(it.txHash)">{{ shortHash(it.txHash) }}</text>
        </view>
        <view v-if="it.dataHash" class="hash">
          <text class="k">摘要</text>
          <text class="hash-val mono" @click="copy(it.dataHash)">{{ shortHash(it.dataHash) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import ChainBadge from '@/components/common/chain-badge.vue'
import EvidenceWall from '@/components/business/evidence-wall.vue'
import { formatDateTime, shortHash } from '@/utils/format'
import { EVENT_TYPE } from '@/config/constants'

defineProps({
  items: { type: Array, default: () => [] }
})

function typeName(t) {
  return EVENT_TYPE[t] || '业务节点'
}

function dotColor(it) {
  if (it.chainStatus === 'FAILED') return '#d93026'
  if (it.chainStatus === 'SUCCESS') return '#1f7a4d'
  if (/不合格|异常|驳回/.test(it.status || '')) return '#d93026'
  return '#94a3b8'
}

function statusClass(s) {
  if (/不合格|异常|驳回/.test(s)) return 'bad'
  if (/合格|通过|正常/.test(s)) return 'good'
  return ''
}

function copy(v) {
  if (!v) return
  uni.setClipboardData({ data: String(v), success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}
</script>

<style lang="scss" scoped>
.timeline {
  padding: 8rpx 0;
}
.node {
  display: flex;
  flex-direction: row;
}
.axis {
  width: 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 8rpx;
  margin-top: 14rpx;
  flex-shrink: 0;
  border: 3rpx solid #fff;
  box-shadow: 0 0 0 2rpx rgba(31, 122, 77, 0.15);
}
.line {
  width: 2rpx;
  flex: 1;
  background: #e2e8f0;
  margin: 6rpx 0;
}
.content {
  flex: 1;
  min-width: 0;
  padding: 0 0 32rpx 12rpx;
}
.last .content {
  padding-bottom: 8rpx;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
.title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-1;
}
.time {
  font-size: 20rpx;
  color: $text-4;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 8rpx;
}
.k {
  font-size: 22rpx;
  color: $text-4;
  width: 74rpx;
  flex-shrink: 0;
}
.v {
  font-size: 24rpx;
  color: $text-2;
  flex: 1;
}
.v.good {
  color: $success;
}
.v.bad {
  color: $danger;
}
.detail {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $text-2;
  line-height: 1.6;
  background: #f7f9fb;
  border-radius: 10rpx;
  padding: 14rpx 16rpx;
}
.chain {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 10rpx;
}
.hash {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 8rpx;
}
.hash-val {
  font-size: 20rpx;
  color: $text-3;
  flex: 1;
}
</style>
