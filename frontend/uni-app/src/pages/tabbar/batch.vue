<template>
  <view class="batch-tab" :class="{ 'as-app': appShell }">
    <view class="toolbar">
      <search-bar v-model="keyword" placeholder="搜索批次号 / 产品名称" @search="onSearch" />
      <scroll-view scroll-x class="filters">
        <view class="filter-row">
          <text
            v-for="f in filters"
            :key="f.value"
            class="filter"
            :class="{ on: status === f.value }"
            @click="pickStatus(f.value)"
          >{{ f.label }}</text>
        </view>
      </scroll-view>
    </view>

    <view class="list">
      <batch-card v-for="b in list" :key="b.id" :batch="b" />
      <empty-state
        v-if="!loading && !list.length"
        :text="keyword ? '没有匹配的批次' : '还没有批次'"
        :action-text="canCreate ? '新建批次' : ''"
        @action="create"
      />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>

    <view v-if="canCreate" class="fab" @click="create">＋</view>

    <!-- 壳内与首页共用同一块 Dock，系统 tabBar 由全局样式隐藏 -->
    <app-dock v-if="appShell" active="batch" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import SearchBar from '@/components/common/search-bar.vue'
import BatchCard from '@/components/business/batch-card.vue'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import { useList } from '@/composables/useList'
import AppDock from '@/components/app/app-dock.vue'
import { isAppUI } from '@/config/platform'
import { useHideTabBar } from '@/utils/app-dock'

/** App 版式（壳内 / 手机浏览器 / 小程序）：改用自绘 Dock，原生 tabBar 收起 */
const appShell = isAppUI()
useHideTabBar()
import { batchPage } from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const store = useUserStore()
const cache = useCacheStore()
const keyword = ref('')
const status = ref('')

const filters = [
  { label: '全部', value: '' },
  { label: '种植中', value: 'PLANTING' },
  { label: '已采收', value: 'HARVESTED' },
  { label: '检测合格', value: 'QUALIFIED' },
  { label: '运输中', value: 'IN_TRANSIT' },
  { label: '销售中', value: 'ON_SALE' },
  { label: '异常', value: 'ABNORMAL' }
]

const canCreate = computed(() => store.hasPerm('batch:create'))

const { list, loading, status: loadStatus, reload, loadMore } = useList(batchPage, {
  pageSize: 10,
  immediate: false,
  filters: () => {
    const f = {}
    if (keyword.value) f.keyword = keyword.value.trim()
    if (status.value) f.status = status.value
    return f
  }
})

function onSearch() {
  reload()
}

function pickStatus(v) {
  status.value = v
  reload()
}

function create() {
  nav(ROUTES.BATCH_CREATE)
}

onShow(() => {
  if (cache.isDirty('batch:list') || !list.value.length) reload()
})

onPullDownRefresh(() => reload())
onReachBottom(() => loadMore())
</script>

<style lang="scss" scoped>
.batch-tab {
  min-height: 100vh;
  background: $bg-page;
}
.toolbar {
  background: #fff;
  padding: 20rpx 24rpx 8rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.filters {
  white-space: nowrap;
  margin-top: 16rpx;
}
.filter-row {
  display: inline-flex;
  flex-direction: row;
  gap: 12rpx;
  padding-bottom: 12rpx;
}
.filter {
  padding: 8rpx 24rpx;
  border-radius: 28rpx;
  background: #f4f6f8;
  color: $text-2;
  font-size: 24rpx;
  flex-shrink: 0;
}
.filter.on {
  background: $brand;
  color: #fff;
}
.list {
  padding: 20rpx 24rpx 40rpx;
}
.batch-tab.as-app .fab {
  /* 让悬浮加号落在 Dock 上方 */
  bottom: calc(150rpx + constant(safe-area-inset-bottom));
  bottom: calc(150rpx + env(safe-area-inset-bottom));
}
.fab {
  position: fixed;
  right: 32rpx;
  bottom: 60rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 48rpx;
  background: $brand-grad;
  color: #fff;
  font-size: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(31, 122, 77, 0.35);
  z-index: 20;
}
</style>
