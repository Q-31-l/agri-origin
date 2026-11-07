<template>
  <view class="empty">
    <view class="icon">{{ icon }}</view>
    <text class="text">{{ text }}</text>
    <view v-if="actionText" class="action" @click="$emit('action')">{{ actionText }}</view>
  </view>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  icon: { type: String, default: '◌' },
  actionText: { type: String, default: '' }
})
defineEmits(['action'])
</script>

<style lang="scss" scoped>
.empty {
  padding: 100rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.icon {
  font-size: 96rpx;
  color: #d3dae2;
  line-height: 1;
}
.text {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: $text-3;
  line-height: 1.7;
  text-align: center;
  white-space: pre-line;
}
.action {
  margin-top: 32rpx;
  padding: 12rpx 40rpx;
  border-radius: 32rpx;
  background: $brand-light;
  color: $brand;
  font-size: 26rpx;
}
</style>
