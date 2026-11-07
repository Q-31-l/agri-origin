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
        <view v-if="list.length > 6" class="search">
          <input v-model="keyword" class="search-input" placeholder="搜批次号 / 作物 / 农场" placeholder-class="ph" />
        </view>
        <scroll-view scroll-y class="list">
          <view
            v-for="b in filtered"
            :key="b.id"
            class="item"
            :class="{ active: String(modelValue) === String(b.id) }"
            @click="choose(b)"
          >
            <view class="col">
              <view class="line1">
                <text class="no mono">{{ b.batchNo }}</text>
                <text class="crop">{{ b.productName }}{{ b.variety ? `（${b.variety}）` : '' }}</text>
              </view>
              <text class="pname">{{ batchDesc(b) || '未关联农场' }}</text>
            </view>
            <status-tag :value="b.status" />
          </view>
          <empty-state v-if="!loading && !filtered.length" :text="emptyText" />
          <view v-if="loading" class="loading">加载中…</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { batchOptions } from '@/api'
import StatusTag from '@/components/common/status-tag.vue'
import EmptyState from '@/components/common/empty-state.vue'
import { batchDesc } from '@/utils/batch-label'

const props = defineProps({
  modelValue: { type: [Number, String], default: '' },
  label: { type: String, default: '批次' },
  placeholder: { type: String, default: '请选择批次' },
  required: { type: Boolean, default: false },
  /** 只列出指定状态的批次，如 'HARVESTED' */
  status: { type: String, default: '' },
  /** 只列出能真正录入的批次（已通过村级核验 + 无异常锁定） */
  usable: { type: Boolean, default: false },
  /** 空态引导：批次为空时告诉用户"为什么没有、该怎么做"（支持 \n 换行） */
  emptyHint: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const show = ref(false)
const loading = ref(false)
const list = ref([])
const selected = ref(null)
const keyword = ref('')

/** 空态文案：搜索不到给"没有匹配"，列表为空给引导（能传 emptyHint 时优先用它） */
const emptyText = computed(() => {
  if (keyword.value.trim()) return '没有匹配的批次'
  return props.emptyHint || '暂无可选批次'
})

/** 批次多起来（上限 200 条）时，靠流水码翻列表不现实，给个搜索 */
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((b) =>
    [b.batchNo, b.productName, b.variety, b.farmName, b.villageName]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw))
  )
})

async function load() {
  loading.value = true
  try {
    const params = {}
    if (props.status) params.status = props.status
    if (props.usable) params.usable = true
    const res = await batchOptions(params)
    list.value = res.data || []
    if (props.modelValue) {
      selected.value = list.value.find((x) => String(x.id) === String(props.modelValue)) || null
      // 从批次详情页带着 batchId 跳进来时，父页面拿不到批次对象（不知道农场地块），
      // 这里补一次 change，让"作业地点/天气"能按正确的地块带出来
      if (selected.value) emit('change', selected.value)
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
.search {
  padding: 16rpx 28rpx;
  border-bottom: 1px solid #f2f5f8;
}
.search-input {
  height: 68rpx;
  background: #f6f8fa;
  border-radius: 34rpx;
  padding: 0 28rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
}
.line1 {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.crop {
  font-size: 24rpx;
  color: $text-2;
  margin-left: 12rpx;
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
