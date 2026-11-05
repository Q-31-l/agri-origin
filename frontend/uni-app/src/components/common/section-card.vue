<template>
  <view class="section">
    <view v-if="title" class="head">
      <view class="left">
        <text class="bar"></text>
        <text class="title">{{ title }}</text>
      </view>
      <slot name="extra"></slot>
    </view>
    <view class="body">
      <slot></slot>
    </view>
  </view>
</template>

<script setup>
defineProps({ title: { type: String, default: '' } })
</script>

<style lang="scss" scoped>
.section {
  background: #fff;
  border-radius: $radius;
  margin: 0 $gap $gap;
  box-shadow: $shadow-card;
  overflow: hidden;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx $gap 8rpx;
}
.left {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.bar {
  width: 6rpx;
  height: 28rpx;
  border-radius: 3rpx;
  background: $brand;
  margin-right: 12rpx;
}
.title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.body {
  padding: 16rpx $gap $gap;
}
</style>
