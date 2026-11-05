<template>
  <view class="pc-qr">
    <image
      v-if="code && !failed"
      :src="url"
      class="img"
      mode="aspectFit"
      @error="failed = true"
      @click="preview"
    />
    <view v-else class="fallback">
      <text class="fb-code">{{ code || '暂无溯源码' }}</text>
      <text class="fb-tip">二维码生成失败，可复制溯源码</text>
    </view>
    <text class="cap">{{ label }}</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ENV } from '@/config/env'

const props = defineProps({
  code: { type: String, default: '' },
  label: { type: String, default: '扫码查看全流程溯源' },
  size: { type: Number, default: 150 }
})

const failed = ref(false)

const url = computed(() => {
  if (!props.code) return ''
  return `${ENV.baseURL}/trace/${encodeURIComponent(props.code)}/qrcode?size=${props.size * 3}`
})

function preview() {
  if (!url.value) return
  uni.previewImage({ urls: [url.value] })
}
</script>

<style lang="scss" scoped>
.pc-qr {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.img {
  width: 150px;
  height: 150px;
  background: #fff;
  border: 1px solid #eef2f6;
  border-radius: 6px;
  cursor: zoom-in;
}
.fallback {
  width: 150px;
  height: 150px;
  border: 1px dashed #cfd8e3;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px;
  box-sizing: border-box;
}
.fb-code {
  font-size: 11px;
  color: #1f2937;
  word-break: break-all;
  text-align: center;
  font-family: Consolas, monospace;
}
.fb-tip {
  font-size: 10px;
  color: #b6bec9;
  margin-top: 6px;
  text-align: center;
}
.cap {
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
}
</style>
