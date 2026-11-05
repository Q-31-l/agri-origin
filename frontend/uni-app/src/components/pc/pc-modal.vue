<template>
  <view v-if="modelValue" class="pc-modal">
    <view class="pc-modal-mask" @click="onMask"></view>
    <view class="pc-modal-box" :style="{ width: width }">
      <view class="pc-modal-head">
        <text class="pc-modal-title">{{ title }}</text>
        <text class="pc-modal-x" @click="close">✕</text>
      </view>
      <view class="pc-modal-body">
        <slot></slot>
      </view>
      <view v-if="!hideFooter" class="pc-modal-foot">
        <view class="pc-btn ghost" @click="close">取消</view>
        <view class="pc-btn" :class="{ danger: danger }" @click="$emit('ok')">{{ okText }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '提示' },
  width: { type: String, default: '460px' },
  okText: { type: String, default: '确定' },
  danger: { type: Boolean, default: false },
  hideFooter: { type: Boolean, default: false },
  maskClosable: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue', 'ok'])

function close() {
  emit('update:modelValue', false)
}

function onMask() {
  if (props.maskClosable) close()
}
</script>

<style lang="scss" scoped>
.pc-modal {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pc-modal-mask {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 30, 22, 0.42);
}
.pc-modal-box {
  position: relative;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(15, 36, 24, 0.24);
  max-width: 92vw;
  max-height: 84vh;
  display: flex;
  flex-direction: column;
}
.pc-modal-head {
  padding: 16px 20px;
  border-bottom: 1px solid #eef2f6;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.pc-modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.pc-modal-x {
  font-size: 13px;
  color: #9aa6b2;
  cursor: pointer;
}
.pc-modal-x:hover {
  color: #d93026;
}
.pc-modal-body {
  padding: 18px 20px;
  overflow-y: auto;
  flex: 1;
}
.pc-modal-foot {
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #eef2f6;
}
</style>
