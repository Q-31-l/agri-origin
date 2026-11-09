<template>
  <view class="todo-page">
    <view class="head">
      <view class="head-row">
        <text class="h-title">审核待办</text>
        <text class="h-count">{{ total }} 项</text>
      </view>
      <text class="h-sub">只显示确实轮到你处理的数据；提交人、提交时间、属于哪个村一目了然</text>
    </view>

    <view v-if="loading" class="tip">加载中…</view>
    <empty-state v-else-if="!groups.length" text="暂无待办。有新的提交时会在这里出现并实时刷新" />

    <view v-for="g in groups" :key="g.type" class="group">
      <view class="g-head">
        <text class="g-title">{{ g.title }}</text>
        <text class="g-count">{{ g.count }}</text>
      </view>

      <view v-for="it in g.items" :key="g.type + '-' + it.id" class="item" @click="openBatch(it)">
        <view class="i-top">
          <text class="i-name">{{ it.productName || '—' }}</text>
          <text class="i-no mono">{{ it.batchNo }}</text>
        </view>
        <text class="i-sum">{{ it.summary }}</text>
        <view class="i-meta">
          <text class="i-by">{{ it.submitBy || '—' }} 提交</text>
          <text class="i-time">{{ formatTime(it.submitTime) }}</text>
        </view>
        <text v-if="it.from" class="i-from">归属：{{ it.from }}</text>
        <view v-if="g.type !== 'ABNORMAL'" class="i-acts">
          <view class="btn ghost" @click.stop="doAudit(g, it, 'REJECTED')">驳回</view>
          <view class="btn" @click.stop="doAudit(g, it, 'APPROVED')">通过</view>
        </view>
        <view v-else class="i-acts">
          <view class="btn" @click.stop="nav(ROUTES.ABNORMAL)">去处置</view>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import EmptyState from '@/components/common/empty-state.vue'
import { workflowTodo, auditBatch, auditPlanting, auditHarvest, auditQuality, auditLogistics, auditSales } from '@/api'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { formatDateTime } from '@/utils/format'
import { toast, confirm } from '@/utils/ui'

const loading = ref(true)
const total = ref(0)
const groups = ref([])

const AUDIT_API = {
  BATCH: auditBatch,
  PLANTING: auditPlanting,
  HARVEST: auditHarvest,
  QUALITY: auditQuality,
  LOGISTICS: auditLogistics,
  SALES: auditSales
}

async function load() {
  loading.value = true
  try {
    const res = await workflowTodo()
    const d = (res && res.data) || {}
    total.value = d.total || 0
    groups.value = (d.groups || []).filter((g) => g.items && g.items.length)
  } catch (e) {
    groups.value = []
    total.value = 0
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function openBatch(it) {
  if (it.batchId) nav(ROUTES.BATCH_DETAIL, { id: it.batchId })
}

function formatTime(t) {
  return t ? formatDateTime(t) : ''
}

async function doAudit(group, item, status) {
  const api = AUDIT_API[group.type]
  if (!api) return
  let remark = ''
  if (status === 'REJECTED') {
    const r = await new Promise((resolve) => {
      uni.showModal({
        title: '驳回并说明原因',
        editable: true,
        placeholderText: '请写明驳回原因，提交人会看到这条意见',
        success: (res) => resolve(res),
        fail: () => resolve({ confirm: false })
      })
    })
    if (!r.confirm) return
    remark = (r.content || '').trim()
    if (!remark) return toast('驳回必须填写原因')
  } else {
    const ok = await confirm('确认审核通过？通过后将进入下一环节')
    if (!ok) return
  }
  try {
    await api(item.id, { auditStatus: status, auditRemark: remark })
    uni.showToast({ title: status === 'APPROVED' ? '已通过' : '已驳回', icon: 'success' })
    await load()
  } catch (e) {
    // request 层已提示
  }
}

onShow(() => load())
onPullDownRefresh(() => load())
</script>

<style lang="scss" scoped>
.todo-page {
  min-height: 100vh;
  background: $bg-page;
}
.head {
  background: #fff;
  padding: 24rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.head-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
.h-title {
  font-size: 32rpx;
  font-weight: 700;
  color: $text-1;
}
.h-count {
  font-size: 24rpx;
  color: $brand;
  font-weight: 600;
}
.h-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $text-4;
  line-height: 1.6;
}
.tip {
  padding: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: $text-3;
}
.group {
  margin: 20rpx 24rpx 0;
}
.g-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
  padding: 8rpx 4rpx;
}
.g-title {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-2;
}
.g-count {
  font-size: 20rpx;
  color: #fff;
  background: #e5a13a;
  border-radius: 20rpx;
  padding: 0 12rpx;
  line-height: 1.8;
}
.item {
  background: #fff;
  border-radius: $radius;
  box-shadow: $shadow-card;
  padding: 24rpx;
  margin-bottom: $gap-sm;
}
.i-top {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
.i-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-1;
}
.i-no {
  font-size: 22rpx;
  color: $text-4;
}
.i-sum {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $text-2;
  line-height: 1.6;
}
.i-meta {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 10rpx;
}
.i-by,
.i-time {
  font-size: 22rpx;
  color: $text-3;
}
.i-from {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $text-4;
}
.i-acts {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1px solid $border;
}
.btn {
  height: 60rpx;
  padding: 0 36rpx;
  border-radius: 30rpx;
  background: $brand;
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
}
.btn.ghost {
  background: #fff;
  color: #d93026;
  border: 1px solid #f0c4c1;
}
.bottom-space {
  height: 60rpx;
}
.mono {
  font-family: Consolas, monospace;
}
</style>
