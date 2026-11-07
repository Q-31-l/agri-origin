<template>
  <view class="batch-list">
    <view class="toolbar">
      <search-bar v-model="keyword" placeholder="批次号 / 产品 / 溯源码" @search="reload" />
      <view class="filter-line">
        <picker mode="selector" :range="statusLabels" @change="onStatusChange">
          <view class="filter-pick">
            <text>{{ statusLabel }}</text>
            <text class="caret">▾</text>
          </view>
        </picker>
        <view class="date-pick" @click="showDate = true">
          <text>{{ dateLabel }}</text>
          <text class="caret">▾</text>
        </view>
        <text class="total">共 {{ total }} 条</text>
      </view>
      <view v-if="showDate" class="date-panel">
        <view class="date-row">
          <text class="dl">开始</text>
          <picker mode="date" :value="startDate" @change="(e) => (startDate = e.detail.value)">
            <text class="dv">{{ startDate || '不限' }}</text>
          </picker>
        </view>
        <view class="date-row">
          <text class="dl">结束</text>
          <picker mode="date" :value="endDate" @change="(e) => (endDate = e.detail.value)">
            <text class="dv">{{ endDate || '不限' }}</text>
          </picker>
        </view>
        <view class="date-actions">
          <view class="btn-sm gray" @click="clearDate">重置</view>
          <view class="btn-sm" @click="applyDate">确定</view>
        </view>
      </view>
    </view>

    <view class="list">
      <batch-card v-for="b in list" :key="b.id" :batch="b" />
      <empty-state v-if="!loading && !list.length" :text="emptyText" />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import SearchBar from '@/components/common/search-bar.vue'
import BatchCard from '@/components/business/batch-card.vue'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import { useList } from '@/composables/useList'
import { useRealtime } from '@/composables/useRealtime'
import { batchPage } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const keyword = ref('')
const status = ref('')
const startDate = ref('')
const endDate = ref('')
const showDate = ref(false)

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '种植中', value: 'PLANTING' },
  { label: '已采收', value: 'HARVESTED' },
  { label: '检测合格', value: 'QUALIFIED' },
  { label: '运输中', value: 'IN_TRANSIT' },
  { label: '销售中', value: 'ON_SALE' },
  { label: '已售罄', value: 'SOLD' },
  { label: '异常管控', value: 'ABNORMAL' },
  { label: '已关闭', value: 'CLOSED' }
]
const statusLabels = STATUS_OPTIONS.map((s) => s.label)
const statusLabel = computed(() => (STATUS_OPTIONS.find((s) => s.value === status.value) || STATUS_OPTIONS[0]).label)

/** 空态文案：农户的批次由生产主体/村管建档时指派，不能自己新建，空列表要给一句能照着做的话 */
const emptyText = computed(() => {
  if (store.roleCode === 'FARMER' && !keyword.value && !status.value && !startDate.value && !endDate.value) {
    return '还没有被指派给你的批次，请联系本村生产主体或村管建档时分配'
  }
  return '没有符合条件的批次'
})

const dateLabel = computed(() => {
  if (!startDate.value && !endDate.value) return '全部时间'
  return `${startDate.value || '不限'} ~ ${endDate.value || '不限'}`
})

const { list, total, loading, status: loadStatus, reload, loadMore } = useList(batchPage, {
  pageSize: 10,
  immediate: true,
  filters: () => {
    const f = {}
    if (keyword.value) f.keyword = keyword.value.trim()
    if (status.value) f.status = status.value
    if (startDate.value) f.startDate = startDate.value
    if (endDate.value) f.endDate = endDate.value
    return f
  }
})

function onStatusChange(e) {
  status.value = STATUS_OPTIONS[e.detail.value].value
  reload()
}

// PC 端或另一台手机写入批次 / 处置异常后，本页自动刷新
useRealtime(() => reload(), { types: ['batch', 'abnormal'] })

function applyDate() {
  showDate.value = false
  reload()
}

function clearDate() {
  startDate.value = ''
  endDate.value = ''
  showDate.value = false
  reload()
}

onPullDownRefresh(() => reload())
onReachBottom(() => loadMore())
</script>

<style lang="scss" scoped>
.batch-list {
  min-height: 100vh;
  background: $bg-page;
}
.toolbar {
  background: #fff;
  padding: 20rpx 24rpx 16rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.filter-line {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 16rpx;
  gap: 16rpx;
}
.filter-pick,
.date-pick {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 60rpx;
  padding: 0 20rpx;
  background: #f4f6f8;
  border-radius: 30rpx;
  font-size: 24rpx;
  color: $text-2;
}
.caret {
  font-size: 18rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.total {
  margin-left: auto;
  font-size: 22rpx;
  color: $text-4;
}
.date-panel {
  margin-top: 16rpx;
  background: #f8fafb;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
}
.date-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 72rpx;
}
.dl {
  width: 80rpx;
  font-size: 24rpx;
  color: $text-3;
}
.dv {
  font-size: 26rpx;
  color: $text-1;
}
.date-actions {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  gap: 16rpx;
  padding-top: 12rpx;
}
.btn-sm {
  height: 60rpx;
  padding: 0 32rpx;
  border-radius: 30rpx;
  background: $brand;
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
}
.btn-sm.gray {
  background: #eef1f4;
  color: $text-2;
}
.list {
  padding: 20rpx 24rpx 40rpx;
}
</style>
