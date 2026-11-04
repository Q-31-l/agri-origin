<template>
  <el-tag :type="meta.type" effect="light" round size="small">{{ meta.label }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ status: { type: String, default: '' } })

const MAP = {
  PLANTING: { label: '种植中', type: 'success' },
  HARVESTED: { label: '已采收', type: 'warning' },
  QUALIFIED: { label: '质检合格', type: 'success' },
  IN_TRANSIT: { label: '运输中', type: 'primary' },
  ON_SALE: { label: '在售', type: 'primary' },
  SOLD: { label: '已售出', type: 'info' },
  ABNORMAL: { label: '异常管控', type: 'danger' },
  CLOSED: { label: '已结案', type: 'info' },
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  PASS: { label: '合格', type: 'success' },
  FAIL: { label: '不合格', type: 'danger' },
  DEPARTED: { label: '运输中', type: 'primary' },
  ARRIVED: { label: '已到达', type: 'success' },
  HANDLING: { label: '处置中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' }
}

const meta = computed(() => MAP[props.status] || { label: props.status || '—', type: 'info' })
</script>
