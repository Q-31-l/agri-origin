<template>
  <view class="pc-more">
    <view class="more-trigger" :class="{ on: open }" @click.stop="toggle">
      <text class="more-label">更多</text>
      <text class="more-caret" :class="{ up: open }">▾</text>
    </view>

    <!-- 透明遮罩：点击空白处收起，比监听 document 更稳 -->
    <view v-if="open" class="more-backdrop" @click.stop="close"></view>

    <view v-if="open" class="more-panel" :class="{ dropup: dropUp }" @click.stop>
      <view
        v-for="(it, i) in visibleItems"
        :key="i"
        class="more-item"
        :class="{ danger: it.danger }"
        @click.stop="pick(it)"
      >
        <text class="more-item-t">{{ it.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { readWindowInfo } from '@/utils/system'

/**
 * 表格操作列的「更多」下拉。
 *
 * 存在的理由：用户管理这类页面一行有 6 个操作，全部平铺会把列挤爆，
 * 右边的"删除"直接被裁掉看不到。主操作留在外面，次要操作收进来。
 *
 * 定位用 absolute 而不是 fixed：外层 .pc-card / .pc-page 都没有 overflow:hidden，
 * 唯一会裁剪的是 .pc-td，所以使用方的操作列需要 overflow:visible（见使用处样式）。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  /** 触发按钮文案 */
  label: { type: String, default: '更多' }
})
const emit = defineEmits(['select'])

const instance = getCurrentInstance()
const open = ref(false)
const dropUp = ref(false)

/** 支持 show:false 隐藏单项，避免父组件在模板里写一堆 v-if */
const visibleItems = computed(() => props.items.filter((i) => i && i.show !== false))

async function toggle() {
  if (open.value) {
    open.value = false
    return
  }
  open.value = true
  dropUp.value = false
  await nextTick()
  measureDirection()
}

function close() {
  open.value = false
}

/**
 * 表格末行向下展开会顶出可视区，这里量一下按钮下方还剩多少空间，
 * 不够就朝上弹。测量失败保持向下，不阻塞交互。
 */
function measureDirection() {
  try {
    const q = uni.createSelectorQuery().in(instance.proxy)
    q.select('.more-trigger').boundingClientRect()
    q.select('.more-panel').boundingClientRect()
    q.exec((res) => {
      if (!res || !res[0] || !res[1]) return
      const btn = res[0]
      const panel = res[1]
      if (!panel.height) return
      const winH = (readWindowInfo().windowHeight || 0) || window.innerHeight || 0
      const below = winH - btn.bottom
      const above = btn.top
      dropUp.value = below < panel.height + 16 && above > below
    })
  } catch (e) {
    /* 平台不支持选择器查询时保持向下展开 */
  }
}

function pick(it) {
  if (it.disabled) return
  open.value = false
  emit('select', it)
}
</script>

<style lang="scss" scoped>
.pc-more {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.more-trigger {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 5px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  transition: all 0.14s ease;
}

.more-trigger:hover,
.more-trigger.on {
  border-color: #c8d4e2;
  background: #f6f9fc;
}

.more-label {
  font-size: 11.5px;
  color: #56636f;
  white-space: nowrap;
}

.more-caret {
  font-size: 10.5px;
  color: #8a99a8;
  line-height: 1;
  transition: transform 0.16s ease;
}

.more-caret.up {
  transform: rotate(180deg);
}

.more-backdrop {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 900;
  background: transparent;
}

.more-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 901;
  min-width: 108px;
  padding: 4px;
  background: #fff;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  box-shadow: 0 8px 24px -8px rgba(16, 24, 40, 0.18), 0 2px 6px rgba(16, 24, 40, 0.06);
}

.more-panel.dropup {
  top: auto;
  bottom: calc(100% + 6px);
}

.more-item {
  padding: 7px 10px;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.12s ease;
}

.more-item:hover {
  background: #f4f7fa;
}

.more-item-t {
  font-size: 11.5px;
  color: #4b5563;
  white-space: nowrap;
}

.more-item.danger .more-item-t {
  color: #d93026;
}

.more-item.danger:hover {
  background: #fdf3f2;
}

.more-item.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
