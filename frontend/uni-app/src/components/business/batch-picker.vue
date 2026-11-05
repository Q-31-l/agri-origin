<template>
  <view class="picker-wrap">
    <view class="row" @click="open">
      <text class="label" :class="{ required }">{{ label }}</text>
      <text class="value" :class="{ ph: !selected }">{{ selected ? selected.batchNo + ' · ' + selected.productName : placeholder }}</text>
      <text class="arrow">›</text>
    </view>

    <view v-if="show" class="mask" @click="show = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">选择批次</text>
          <text class="close" @click="show = false">×</text>
        </view>
        <scroll-view scroll-y class="list">
          <view
            v-for="b in list"
            :key="b.id"
            class="item"
            :class="{ active: modelValue === b.id }"
            @click="choose(b)"
          >
            <view class="col">
              <text class="no mono">{{ b.batchNo }}</text>
              <text class="pname">{{ b.productName }} · {{ b.farmName || '未关联农场' }}</text>
            </view>
            <status-tag :value="b.status" />
          </view>
          <empty-state v-if="!loading && !list.length" text="暂无可选批次" />
          <view v-if="loading" class="loading">加载中…</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { batchOptions } from '@/api'
import StatusTag from '@/components/common/status-tag.vue'
import EmptyState from '@/components/common/empty-state.vue'

const props = defineProps({
  modelValue: { type: [Number, String], default: '' },
  label: { type: String, default: '批次' },
  placeholder: { type: String, default: '请选择批次' },
  required: { type: Boolean, default: false },
  /** 只列出指定状态的批次，如 'HARVESTED' */
  status: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const show = ref(false)
const loading = ref(false)
const list = ref([])
const selected = ref(null)

async function load() {
  loading.value = true
  try {
    const res = await batchOptions(props.status ? { status: props.status } : {})
    list.value = res.data || []
    if (props.modelValue) {
      selected.value = list.value.find((x) => String(x.id) === String(props.modelValue)) || null
    }
  } finally {
    loading.value = false
  }
}

function open() {
  show.value = true
  if (!list.value.length) load()
}

function choose(b) {
  selected.value = b
  show.value = false
  emit('update:modelValue', b.id)
  emit('change', b)
}

onMounted(() => {
  if (props.modelValue) load()
})

defineExpose({ load, selected })
</script>

<style lang="scss" scoped>
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1px solid $border;
}
.label {
  width: 180rpx;
  font-size: 28rpx;
  color: $text-2;
  flex-shrink: 0;
}
.value {
  flex: 1;
  font-size: 28rpx;
  color: $text-1;
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.value.ph {
  color: $text-4;
}
.arrow {
  font-size: 32rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 900;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.sheet {
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}
.sheet-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx;
  border-bottom: 1px solid $border;
}
.sheet-title {
  font-size: 30rpx;
  font-weight: 600;
}
.close {
  font-size: 40rpx;
  color: $text-3;
  line-height: 1;
}
.list {
  max-height: 60vh;
  padding: 8rpx 0;
}
.item {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 28rpx;
  border-bottom: 1px solid #f2f5f8;
}
.item.active {
  background: $brand-light;
}
.no {
  font-size: 26rpx;
  color: $text-1;
}
.pname {
  font-size: 22rpx;
  color: $text-3;
  margin-top: 4rpx;
}
.loading {
  text-align: center;
  padding: 40rpx;
  color: $text-3;
  font-size: 24rpx;
}
</style>
