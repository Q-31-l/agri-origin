<template>
  <text class="tag" :style="{ color: conf.color, background: conf.bg }">{{ conf.text || text }}</text>
</template>

<script setup>
import { computed } from 'vue'
import { BATCH_STATUS, AUDIT_STATUS, CHAIN_STATUS, QUALITY_RESULT } from '@/config/constants'

const props = defineProps({
  value: { type: String, default: '' },
  /** batch | audit | chain | quality */
  type: { type: String, default: 'batch' },
  text: { type: String, default: '' }
})

const MAPS = {
  batch: BATCH_STATUS,
  audit: AUDIT_STATUS,
  chain: CHAIN_STATUS,
  quality: QUALITY_RESULT
}

const conf = computed(() => {
  const map = MAPS[props.type] || BATCH_STATUS
  return map[props.value] || { text: props.text || props.value || '-', color: '#64748b', bg: '#f1f2f4' }
})
</script>

<style lang="scss" scoped>
.tag {
  display: inline-flex;
  align-items: center;
  padding: 2rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  line-height: 1.7;
  white-space: nowrap;
}
</style>
