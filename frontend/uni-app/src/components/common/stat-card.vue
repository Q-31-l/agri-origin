<template>
  <view class="stat" :style="{ background: bg }">
    <text class="label">{{ label }}</text>
    <view class="value-row">
      <text class="value" :style="{ color: color }">{{ value }}</text>
      <text v-if="unit" class="unit">{{ unit }}</text>
    </view>
    <text v-if="hint" class="hint">{{ hint }}</text>
  </view>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: 0 },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  color: { type: String, default: '#1f2937' },
  bg: { type: String, default: '#ffffff' }
})
</script>

<style lang="scss" scoped>
.stat {
  flex: 1;
  border-radius: $radius;
  padding: 24rpx;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
}
.label {
  font-size: 24rpx;
  color: $text-3;
}
.value-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-top: 8rpx;
}
.value {
  font-size: 46rpx;
  font-weight: 700;
  line-height: 1.1;
}
.unit {
  font-size: 22rpx;
  color: $text-3;
  margin-left: 6rpx;
}
.hint {
  font-size: 20rpx;
  color: $text-4;
  margin-top: 6rpx;
}
</style>
