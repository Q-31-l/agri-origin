<template>
  <view class="pc-sel" :style="{ width: width }">
    <view class="pc-sel-box" @click.stop="toggle">
      <text class="pc-sel-text" :class="{ ph: !currentLabel }">{{ currentLabel || placeholder }}</text>
      <text class="pc-sel-arrow" :class="{ up: open }">▾</text>
    </view>

    <view v-if="open" class="pc-sel-mask" @click="open = false"></view>
    <view v-if="open" class="pc-sel-pop" :style="popWidth ? { width: popWidth } : {}">
      <view
        v-for="o in options"
        :key="String(o.value)"
        class="pc-sel-item"
        :class="{ on: o.value === modelValue, two: !!o.desc }"
        @click="pick(o)"
      >
        <view class="pc-sel-main">
          <text class="pc-sel-label">{{ o.label }}</text>
          <text v-if="o.desc" class="pc-sel-desc">{{ o.desc }}</text>
        </view>
        <text v-if="o.tag" class="pc-sel-tag">{{ o.tag }}</text>
        <text v-if="o.value === modelValue" class="pc-sel-check">✓</text>
      </view>
      <view v-if="!options.length" class="pc-sel-empty">暂无可选项</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, Boolean], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  width: { type: String, default: '130px' },
  /** 下拉面板宽度。选项带 desc 时建议给宽一点，否则两行文字会挤成一条 */
  popWidth: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)

const currentLabel = computed(() => {
  const hit = props.options.find((o) => o.value === props.modelValue)
  return hit ? hit.label : ''
})

function toggle() {
  open.value = !open.value
}

function pick(o) {
  open.value = false
  if (o.value === props.modelValue) return
  emit('update:modelValue', o.value)
  emit('change', o.value)
}
</script>

<style lang="scss" scoped>
.pc-sel {
  position: relative;
  flex-shrink: 0;
}
.pc-sel-box {
  height: 32px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #fff;
  padding: 0 8px 0 10px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}
.pc-sel-box:hover {
  border-color: #1f7a4d;
}
.pc-sel-text {
  font-size: 12px;
  color: #4b5563;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.pc-sel-text.ph {
  color: #b6bec9;
}
.pc-sel-arrow {
  font-size: 10px;
  color: #9aa6b2;
  margin-left: 6px;
  transition: transform 0.15s;
}
.pc-sel-arrow.up {
  transform: rotate(180deg);
}
.pc-sel-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 900;
}
.pc-sel-pop {
  position: absolute;
  z-index: 901;
  left: 0;
  top: 36px;
  min-width: 100%;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 36, 24, 0.1);
  padding: 4px;
}
.pc-sel-item {
  min-height: 30px;
  border-radius: 5px;
  padding: 0 8px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  white-space: nowrap;
}
.pc-sel-item.two {
  padding: 7px 8px;
  align-items: flex-start;
}
.pc-sel-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.pc-sel-desc {
  font-size: 11px;
  color: #93a0ad;
  margin-top: 3px;
  line-height: 1.35;
  white-space: normal;
}
.pc-sel-tag {
  flex-shrink: 0;
  margin-left: 10px;
  font-size: 10.5px;
  color: #1f7a4d;
  background: #eaf6ee;
  border-radius: 3px;
  padding: 1px 6px;
}
.pc-sel-empty {
  padding: 12px 10px;
  font-size: 12px;
  color: #9aa6b2;
  text-align: center;
}
.pc-sel-item:hover {
  background: #f2f7f4;
}
.pc-sel-item.on {
  background: #e8f5ee;
}
.pc-sel-label {
  font-size: 12px;
  color: #4b5563;
}
.pc-sel-item.on .pc-sel-label {
  color: #1f7a4d;
  font-weight: 500;
}
.pc-sel-check {
  font-size: 11px;
  color: #1f7a4d;
  margin-left: 8px;
}
</style>
