<template>
  <view class="list-page">
    <view class="toolbar">
      <search-bar v-model="keyword" placeholder="搜索批次号 / 农事内容" @search="reload" />
    </view>

    <view class="list">
      <view v-for="p in list" :key="p.id" class="row-card" @click="toBatch(p)">
        <view class="rc-head">
          <view class="row">
            <text class="type">{{ plantingTypeLabel(p.recordType) }}</text>
            <status-tag v-if="p.auditStatus" :value="p.auditStatus" type="audit" />
          </view>
          <text class="date">{{ formatDate(p.operationDate) }}</text>
        </view>
        <text class="content">{{ p.content }}</text>
        <text v-if="p.auditStatus === 'PENDING'" class="audit-info pending">已提交，等待村管审核</text>
        <text v-else-if="p.auditStatus === 'REJECTED'" class="audit-info danger">
          已驳回{{ p.auditRemark ? '：' + p.auditRemark : '' }}，请修正后重新提交
        </text>
        <text v-else-if="p.auditTime" class="audit-info">
          审核：{{ p.auditBy || '村管' }} · {{ p.auditTime }}
        </text>
        <view class="rc-foot">
          <text class="batch mono">{{ p.batchNo }}</text>
          <chain-badge :status="p.chainStatus" :block-number="p.blockNumber" />
        </view>
      </view>
      <empty-state v-if="!loading && !list.length" text="暂无农事记录" />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import SearchBar from '@/components/common/search-bar.vue'
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import { useList } from '@/composables/useList'
import { plantingPage } from '@/api'
import { formatDate } from '@/utils/format'
import { plantingTypeLabel } from '@/config/constants'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const keyword = ref('')

const { list, loading, status, reload, loadMore } = useList(plantingPage, {
  pageSize: 10,
  filters: () => (keyword.value ? { keyword: keyword.value.trim() } : {})
})

function toBatch(p) {
  if (!p.batchId) {
    // 手写作物的农事在村管审核通过前还没有批次档案，跳详情只会得到空白页
    uni.showToast({
      title: p.auditStatus === 'PENDING' ? '待村管审核，通过后自动建档' : '该记录尚未关联批次',
      icon: 'none'
    })
    return
  }
  nav(ROUTES.BATCH_DETAIL, { id: p.batchId })
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
.row-card {
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  margin-bottom: $gap-sm;
  box-shadow: $shadow-card;
}
.rc-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
}
.type {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-1;
}
.date {
  font-size: 22rpx;
  color: $text-4;
}
.content {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $text-2;
  line-height: 1.6;
}
.audit-info {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $text-4;
}
.audit-info.pending {
  color: #b45309;
}
.audit-info.danger {
  color: #d93026;
}
.rc-foot {
  margin-top: 14rpx;
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
</style>
