<template>
  <view class="pc-pager">
    <text class="pc-pager-info">共 {{ total }} 条 · 第 {{ page }}/{{ pages }} 页</text>
    <view class="pc-page-btn" :class="{ disabled: page <= 1 }" @click="go(page - 1)">上一页</view>
    <view
      v-for="p in nums"
      :key="p"
      class="pc-page-btn"
      :class="{ on: p === page }"
      @click="go(p)"
    >
      {{ p }}
    </view>
    <view class="pc-page-btn" :class="{ disabled: page >= pages }" @click="go(page + 1)">下一页</view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 }
})
const emit = defineEmits(['change'])

const pages = computed(() => Math.max(1, Math.ceil(props.total / (props.size || 10))))

const nums = computed(() => {
  const total = pages.value
  const cur = props.page
  const span = 5
  let start = Math.max(1, cur - Math.floor(span / 2))
  let end = start + span - 1
  if (end > total) {
    end = total
    start = Math.max(1, end - span + 1)
  }
  const arr = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})

function go(p) {
  if (p < 1 || p > pages.value || p === props.page) return
  emit('change', p)
}
</script>
