<template>
  <view class="qr">
    <image v-if="!failed" :src="url" class="img" mode="aspectFit" @error="failed = true" @click="preview" />
    <view v-else class="fallback">
      <text class="code mono">{{ code }}</text>
      <text class="tip">二维码生成失败，可复制溯源码</text>
    </view>
    <text class="label">{{ label }}</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ENV } from '@/config/env'

const props = defineProps({
  code: { type: String, default: '' },
  label: { type: String, default: '扫码查看全流程溯源' },
  size: { type: Number, default: 220 }
})

const failed = ref(false)

const url = computed(() => {
  if (!props.code) return ''
  return `${ENV.baseURL}/trace/${encodeURIComponent(props.code)}/qrcode?size=${props.size * 2}`
})

function preview() {
  if (!url.value) return
  uni.previewImage({ urls: [url.value] })
}
</script>

<style lang="scss" scoped>
.qr {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.img {
  width: 220rpx;
  height: 220rpx;
  background: #fff;
}
.fallback {
  width: 220rpx;
  height: 220rpx;
  border: 1px dashed #cfd8e3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12rpx;
}
.code {
  font-size: 22rpx;
  color: $text-1;
  word-break: break-all;
  text-align: center;
}
.tip {
  font-size: 18rpx;
  color: $text-4;
  margin-top: 8rpx;
  text-align: center;
}
.label {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $text-3;
}
</style>
