<template>
  <view class="search">
    <app-icon name="search" color="#9ca3af" :size="36" class="icon" />
    <input
      class="input"
      :value="modelValue"
      :placeholder="placeholder"
      placeholder-class="ph"
      confirm-type="search"
      @input="onInput"
      @confirm="onConfirm"
    />
    <text v-if="modelValue" class="clear" @click="clear">×</text>
  </view>
</template>

<script setup>
import AppIcon from '@/components/common/app-icon.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' }
})
const emit = defineEmits(['update:modelValue', 'search'])

let timer = null

function onInput(e) {
  const v = e.detail.value
  emit('update:modelValue', v)
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => emit('search', v), 400)
}

function onConfirm(e) {
  emit('search', e.detail.value)
}

function clear() {
  emit('update:modelValue', '')
  emit('search', '')
}
</script>

<style lang="scss" scoped>
.search {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 76rpx;
  background: #fff;
  border-radius: 38rpx;
  padding: 0 24rpx;
  border: 1px solid $border;
}
.icon {
  font-size: 32rpx;
  color: $text-4;
  margin-right: 8rpx;
}
.input {
  flex: 1;
  font-size: 26rpx;
  color: $text-1;
}
.ph {
  color: $text-4;
}
.clear {
  font-size: 32rpx;
  color: $text-4;
  padding: 0 4rpx;
}
</style>
