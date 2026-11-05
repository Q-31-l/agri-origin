<template>
  <view class="chain">
    <view class="status-card">
      <view class="s-left">
        <text class="s-title">联盟链存证</text>
        <text class="s-sub">FISCO-BCOS · 关键业务数据摘要上链</text>
      </view>
      <view class="s-right">
        <text class="s-dot" :class="{ on: chainOn }"></text>
        <text class="s-state">{{ chainOn ? '运行中' : '演示模式' }}</text>
      </view>
    </view>

    <view class="verify-box">
      <text class="v-title">按批次号校验</text>
      <view class="v-row">
        <input v-model="batchNo" class="v-input" placeholder="输入批次号，如 AG20260512001" placeholder-class="ph" />
        <view class="v-btn" @click="doVerify">校验</view>
      </view>
    </view>

    <view class="list">
      <view v-for="r in list" :key="r.id" class="rec">
        <view class="rec-head">
          <text class="evt">{{ eventName(r.eventType) }}</text>
          <chain-badge :status="r.chainStatus" :block-number="r.blockNumber" />
        </view>
        <view class="rec-row">
          <text class="k">批次</text>
          <text class="v mono">{{ r.batchNo || '-' }}</text>
        </view>
        <view class="rec-row">
          <text class="k">摘要</text>
          <text class="v mono" @click="copy(r.dataHash)">{{ shortHash(r.dataHash) }}</text>
        </view>
        <view class="rec-row">
          <text class="k">交易</text>
          <text class="v mono" @click="copy(r.txHash)">{{ shortHash(r.txHash) }}</text>
        </view>
        <text class="time">{{ formatDateTime(r.createTime) }}</text>
      </view>
      <empty-state v-if="!loading && !list.length" text="暂无存证记录" />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import ChainBadge from '@/components/common/chain-badge.vue'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import { useList } from '@/composables/useList'
import { chainPage, chainStatus, chainVerifyByNo } from '@/api'
import { EVENT_TYPE } from '@/config/constants'
import { formatDateTime, shortHash } from '@/utils/format'
import { toast } from '@/utils/ui'

const batchNo = ref('')
const chainOn = ref(true)

const { list, loading, status, reload, loadMore } = useList(chainPage, { pageSize: 10 })

function eventName(t) {
  return EVENT_TYPE[t] || t || '业务事件'
}

function copy(v) {
  if (!v) return
  uni.setClipboardData({ data: String(v), success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}

async function doVerify() {
  const no = batchNo.value.trim()
  if (!no) return toast('请输入批次号')
  try {
    const res = await chainVerifyByNo(no)
    const d = res.data || {}
    uni.showModal({
      title: d.passed ? '校验通过' : '校验未通过',
      content: `${d.message || ''}\n记录比对：${d.matchedRecords || 0}/${d.totalRecords || 0}`,
      showCancel: false
    })
  } catch (e) {
    /* 已提示 */
  }
}

onLoad(async () => {
  try {
    const res = await chainStatus()
    chainOn.value = !!(res.data && res.data.enabled)
  } catch (e) {
    chainOn.value = false
  }
})

onPullDownRefresh(() => reload())
onReachBottom(() => loadMore())
</script>

<style lang="scss" scoped>
.chain {
  min-height: 100vh;
  background: $bg-page;
}
.status-card {
  background: $brand-grad;
  padding: 32rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.s-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #fff;
}
.s-sub {
  display: block;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.72);
  margin-top: 6rpx;
}
.s-right {
  display: flex;
  flex-direction: row;
  align-items: center;
  background: rgba(255, 255, 255, 0.16);
  padding: 8rpx 18rpx;
  border-radius: 20rpx;
}
.s-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 5rpx;
  background: #fbbf24;
  margin-right: 8rpx;
}
.s-dot.on {
  background: #4ade80;
}
.s-state {
  font-size: 22rpx;
  color: #fff;
}
.verify-box {
  margin: 24rpx;
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  box-shadow: $shadow-card;
}
.v-title {
  font-size: 26rpx;
  color: $text-2;
}
.v-row {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  margin-top: 14rpx;
}
.v-input {
  flex: 1;
  height: 76rpx;
  background: #f6f8fa;
  border-radius: 10rpx;
  padding: 0 18rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
  font-size: 24rpx;
}
.v-btn {
  width: 140rpx;
  height: 76rpx;
  border-radius: 10rpx;
  background: $brand;
  color: #fff;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.list {
  padding: 0 24rpx 40rpx;
}
.rec {
  background: #fff;
  border-radius: $radius;
  padding: 22rpx 24rpx;
  margin-bottom: $gap-sm;
  box-shadow: $shadow-card;
}
.rec-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10rpx;
}
.evt {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-1;
}
.rec-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 4rpx 0;
}
.k {
  width: 70rpx;
  font-size: 20rpx;
  color: $text-4;
}
.v {
  flex: 1;
  font-size: 22rpx;
  color: $text-2;
}
.time {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $text-4;
  text-align: right;
}
</style>
