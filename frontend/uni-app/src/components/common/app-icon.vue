<template>
  <!--
    移动端图标（H5 / 小程序 / App 通用）。
    用 <image> 加载 base64 PNG，而不是内联 SVG——微信小程序模板不支持 <svg>。
    字形与 PC 端同出 src/config/icons.js，风格一致。
  -->
  <image class="app-icon" :src="src" :style="boxStyle" mode="aspectFit" />
</template>

<script setup>
import { computed } from 'vue'
import { pngIcon } from '@/config/icons-png'

const props = defineProps({
  name: { type: String, default: '' },
  color: { type: String, default: '#374151' },
  /** 数字按 rpx 处理（750 设计稿），也可传 '32px' 这样的字符串 */
  size: { type: [Number, String], default: 44 }
})

const src = computed(() => pngIcon(props.name, props.color))

const boxStyle = computed(() => {
  const s = typeof props.size === 'number' ? props.size + 'rpx' : props.size
  return { width: s, height: s }
})
</script>

<style scoped>
.app-icon {
  display: block;
  flex: 0 0 auto;
}
</style>
