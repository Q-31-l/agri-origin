<template>
  <view class="load-more">
    <text v-if="status === 'loading'">加载中…</text>
    <text v-else-if="status === 'noMore'">— 已经到底了 —</text>
    <text v-else-if="status === 'error'" class="err" @click="$emit('retry')">加载失败，点击重试</text>
    <text v-else>上拉加载更多</text>
  </view>
</template>

<script setup>
defineProps({
  status: { type: String, default: 'more' } // more | loading | noMore | error
})
defineEmits(['retry'])
</script>

<style lang="scss" scoped>
.load-more {
  padding: 32rpx 0 48rpx;
  text-align: center;
  font-size: 24rpx;
  color: $text-4;
}
.err {
  color: $danger;
}
</style>
