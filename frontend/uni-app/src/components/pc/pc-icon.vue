<template>
  <!--
    PC 端图标：内联 SVG，颜色跟随父级 color（stroke=currentColor）。
    只在 H5（含 PC 布局）使用；移动端三端共用的页面请用 png-icon.vue，
    因为微信小程序的模板不支持 <svg> 标签。
  -->
  <svg
    class="pc-icon"
    :style="boxStyle"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="markup"
  ></svg>
</template>

<script setup>
import { computed } from 'vue'
import { iconMarkup } from '@/config/icons'

const props = defineProps({
  name: { type: String, default: '' },
  size: { type: [Number, String], default: 18 },
  /** 线宽：默认 1.7，小尺寸（14 以下）建议 1.9 免得糊 */
  strokeWidth: { type: [Number, String], default: 1.7 }
})

const markup = computed(() => iconMarkup(props.name))

const boxStyle = computed(() => {
  const s = typeof props.size === 'number' ? props.size + 'px' : props.size
  return { width: s, height: s, flex: '0 0 auto', display: 'block' }
})
</script>

<style scoped>
.pc-icon {
  overflow: visible;
}
</style>
