<template>
  <view class="list-page">
    <view class="toolbar">
      <search-bar v-model="keyword" placeholder="搜索报告编号 / 批次号" @search="reload" />
    </view>

    <view class="list">
      <view v-for="q in list" :key="q.id" class="card" @click="toBatch(q)">
        <view class="head">
          <view class="col flex1">
            <text class="no mono">{{ q.reportNo }}</text>
            <text class="org">{{ q.inspectOrg }} · {{ formatDate(q.inspectDate) }}</text>
          </view>
          <status-tag :value="q.inspectResult" type="quality" />
        </view>
        <view class="foot">
          <text class="batch mono">{{ q.batchNo }}</text>
          <chain-badge :status="q.chainStatus" :block-number="q.blockNumber" />
        </view>
      </view>
      <empty-state v-if="!loading && !list.length" text="暂无质检报告" />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>

    <view v-if="canCreate" class="fab" @click="create">＋</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import SearchBar from '@/components/common/search-bar.vue'
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import { useList } from '@/composables/useList'
import { qualityPage } from '@/api'
import { useUserStore } from '@/store/user'
import { formatDate } from '@/utils/format'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const store = useUserStore()
const keyword = ref('')
const canCreate = computed(() => store.hasPerm('quality:create'))

const { list, loading, status, reload, loadMore } = useList(qualityPage, {
  pageSize: 10,
  filters: () => (keyword.value ? { keyword: keyword.value.trim() } : {})
})

function toBatch(q) {
  nav(ROUTES.BATCH_DETAIL, { id: q.batchId })
}
function create() {
  nav(ROUTES.QUALITY_FORM)
}

onPullDownRefresh(() => reload())
onReachBottom(() => loadMore())
</script>

<style lang="scss" scoped>
.list-page {
  min-height: 100vh;
  background: $bg-page;
}
.toolbar {
  background: #fff;
  padding: 20rpx 24rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.list {
  padding: 20rpx 24rpx 40rpx;
}
.card {
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  margin-bottom: $gap-sm;
  box-shadow: $shadow-card;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
}
.no {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-1;
}
.org {
  font-size: 22rpx;
  color: $text-3;
  margin-top: 6rpx;
}
.foot {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1px solid $border;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.batch {
  font-size: 22rpx;
  color: $text-3;
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
}
</style>
