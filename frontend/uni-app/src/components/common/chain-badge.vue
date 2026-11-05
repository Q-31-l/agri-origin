<template>
  <view class="badge" :class="status" @click="$emit('click')">
    <text class="dot"></text>
    <text class="txt">{{ label }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { CHAIN_STATUS } from '@/config/constants'

const props = defineProps({
  status: { type: String, default: 'NONE' },
  blockNumber: { type: [Number, String], default: '' }
})
defineEmits(['click'])

const label = computed(() => {
  const c = CHAIN_STATUS[props.status] || CHAIN_STATUS.NONE
  if (props.status === 'SUCCESS' && props.blockNumber) return `${c.text} #${props.blockNumber}`
  return c.text
})
</script>

<style lang="scss" scoped>
.badge {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  padding: 4rpx 14rpx;
  border-radius: 20rpx;
  font-size: 20rpx;
}
.dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 4rpx;
  margin-right: 8rpx;
  background: currentColor;
}
.SUCCESS {
  color: $success;
  background: $success-light;
}
.FAILED {
  color: $danger;
  background: $danger-light;
}
.SKIPPED {
  color: #b45309;
  background: #fef6e0;
}
.NONE {
  color: $info;
  background: $info-light;
}
</style>
