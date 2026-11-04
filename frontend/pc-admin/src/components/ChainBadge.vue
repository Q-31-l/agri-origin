<template>
  <span class="badge" :class="cls">
    <span class="dot"></span>{{ text }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ status: { type: String, default: '' } })

const text = computed(() => {
  const map = { SUCCESS: '已上链', FAILED: '上链失败', SKIPPED: '未上链', NONE: '未上链' }
  return map[props.status] || '未上链'
})

const cls = computed(() => {
  if (props.status === 'SUCCESS') return 'ok'
  if (props.status === 'FAILED') return 'fail'
  return 'skip'
})
</script>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  border-radius: 999px;
  padding: 2px 10px;
  border: 1px solid transparent;
  white-space: nowrap;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.badge.ok {
  color: #1f7a4c;
  background: #eaf7f0;
  border-color: #c8ead7;
}

.badge.ok .dot {
  background: #2f9e63;
}

.badge.fail {
  color: #b93b3b;
  background: #fdeeee;
  border-color: #f7cccc;
}

.badge.fail .dot {
  background: #e05252;
}

.badge.skip {
  color: #6b7c8f;
  background: #f2f5f8;
  border-color: #e2e8ef;
}

.badge.skip .dot {
  background: #b6c2cf;
}
</style>
