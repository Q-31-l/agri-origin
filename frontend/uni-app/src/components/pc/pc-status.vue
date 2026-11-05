<template>
  <text class="pc-tag" :style="{ color: conf.color, background: conf.bg }">{{ conf.text }}</text>
</template>

<script setup>
import { computed } from 'vue'
import { BATCH_STATUS, AUDIT_STATUS, CHAIN_STATUS, QUALITY_RESULT } from '@/config/constants'

const props = defineProps({
  value: { type: String, default: '' },
  /** batch | audit | chain | quality */
  type: { type: String, default: 'batch' }
})

const MAPS = {
  batch: BATCH_STATUS,
  audit: AUDIT_STATUS,
  chain: CHAIN_STATUS,
  quality: QUALITY_RESULT
}

const conf = computed(() => {
  const map = MAPS[props.type] || BATCH_STATUS
  return (
    map[props.value] || {
      text: props.value || '-',
      color: '#64748b',
      bg: '#f1f2f4'
    }
  )
})
</script>
