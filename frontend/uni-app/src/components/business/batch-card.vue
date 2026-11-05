<template>
  <view class="batch-card" @click="open">
    <view class="top">
      <view class="left">
        <text class="no mono">{{ batch.batchNo }}</text>
        <status-tag :value="batch.status" />
      </view>
      <chain-badge :status="batch.chainStatus" :block-number="batch.blockNumber" />
    </view>

    <view class="mid">
      <text class="product">{{ batch.productName }}</text>
      <text v-if="batch.variety" class="variety">{{ batch.variety }}</text>
    </view>

    <view class="meta">
      <text class="meta-item">农场：{{ batch.farmName || '-' }}</text>
      <text class="meta-item">数量：{{ formatQty(batch.quantity, batch.unit) }}</text>
    </view>

    <view class="foot">
      <text class="time">{{ formatDate(batch.plantingDate) }} 种植</text>
      <text v-if="batch.abnormalFlag" class="warn">异常批次</text>
    </view>
  </view>
</template>

<script setup>
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import { formatDate, formatQty } from '@/utils/format'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const props = defineProps({
  batch: { type: Object, required: true }
})

function open() {
  nav(ROUTES.BATCH_DETAIL, { id: props.batch.id })
}
</script>

<style lang="scss" scoped>
.batch-card {
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  box-shadow: $shadow-card;
  margin-bottom: $gap-sm;
  position: relative;
  overflow: hidden;
}
.batch-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: $brand;
}
.top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.left {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
}
.no {
  font-size: 24rpx;
  color: $text-3;
}
.mid {
  margin-top: 14rpx;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
.product {
  font-size: 34rpx;
  font-weight: 600;
  color: $text-1;
}
.variety {
  font-size: 24rpx;
  color: $text-3;
  margin-left: 12rpx;
}
.meta {
  margin-top: 12rpx;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 24rpx;
}
.meta-item {
  font-size: 24rpx;
  color: $text-2;
}
.foot {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid $border;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.time {
  font-size: 22rpx;
  color: $text-4;
}
.warn {
  font-size: 22rpx;
  color: $danger;
}
</style>
