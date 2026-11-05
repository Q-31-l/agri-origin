<template>
  <view class="report">
    <view class="head">
      <view class="col flex1">
        <text class="no mono">{{ report.reportNo }}</text>
        <text class="org">{{ report.inspectOrg }}</text>
      </view>
      <status-tag :value="report.inspectResult" type="quality" />
    </view>

    <view class="grid">
      <view class="cell">
        <text class="k">检测日期</text>
        <text class="v">{{ formatDate(report.inspectDate) }}</text>
      </view>
      <view class="cell">
        <text class="k">执行标准</text>
        <text class="v">{{ report.standardNo || '-' }}</text>
      </view>
      <view class="cell">
        <text class="k">检测人</text>
        <text class="v">{{ report.inspectorName || '-' }}</text>
      </view>
      <view class="cell">
        <text class="k">存证</text>
        <chain-badge :status="report.chainStatus" :block-number="report.blockNumber" />
      </view>
    </view>

    <view v-if="items.length" class="items">
      <view class="item-head">
        <text class="c1">检测项</text>
        <text class="c2">实测值</text>
        <text class="c3">限量</text>
        <text class="c4">结论</text>
      </view>
      <view v-for="(it, i) in items" :key="i" class="item-row">
        <text class="c1">{{ it.item }}</text>
        <text class="c2">{{ it.value }}{{ it.unit || '' }}</text>
        <text class="c3">{{ it.limit }}{{ it.unit || '' }}</text>
        <text class="c4" :class="{ bad: it.result !== '合格' }">{{ it.result }}</text>
      </view>
    </view>

    <view v-if="report.conclusion" class="conclusion">
      <text class="k">结论</text>
      <text class="txt">{{ report.conclusion }}</text>
    </view>

    <view class="foot">
      <text v-if="report.fileHash" class="hash mono">报告摘要 {{ shortHash(report.fileHash) }}</text>
      <text v-if="report.filePath" class="link" @click="openFile">查看报告 PDF</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import { formatDate, shortHash } from '@/utils/format'
import { resolveFileUrl } from '@/utils/file'

const props = defineProps({
  report: { type: Object, required: true }
})

const items = computed(() => {
  if (!props.report.itemsJson) return []
  try {
    const arr = JSON.parse(props.report.itemsJson)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
})

function openFile() {
  const url = resolveFileUrl(props.report.filePath)
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url,
    success: (res) => {
      uni.openDocument({ filePath: res.tempFilePath, showMenu: true, fail: () => uni.showToast({ title: '无法打开该文件', icon: 'none' }) })
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' })
  })
  // #endif
}
</script>

<style lang="scss" scoped>
.report {
  border: 1px solid $border;
  border-radius: 12rpx;
  padding: 20rpx;
  background: #fff;
}
.report + .report {
  margin-top: 16rpx;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
}
.no {
  font-size: 24rpx;
  color: $text-1;
  font-weight: 600;
}
.org {
  font-size: 22rpx;
  color: $text-3;
  margin-top: 4rpx;
}
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 16rpx;
}
.cell {
  width: 50%;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 0;
}
.k {
  font-size: 22rpx;
  color: $text-4;
  margin-right: 10rpx;
  flex-shrink: 0;
}
.v {
  font-size: 22rpx;
  color: $text-2;
}
.items {
  margin-top: 16rpx;
  border-top: 1px solid $border;
  padding-top: 12rpx;
}
.item-head,
.item-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 0;
}
.item-head text {
  font-size: 20rpx;
  color: $text-4;
}
.item-row text {
  font-size: 22rpx;
  color: $text-2;
}
.c1 { flex: 2; }
.c2 { flex: 1.2; }
.c3 { flex: 1.2; }
.c4 { flex: 1; text-align: right; color: $success; }
.c4.bad { color: $danger; }
.conclusion {
  margin-top: 12rpx;
  background: #f7f9fb;
  border-radius: 10rpx;
  padding: 14rpx;
  display: flex;
  flex-direction: row;
}
.conclusion .txt {
  flex: 1;
  font-size: 22rpx;
  color: $text-2;
  line-height: 1.6;
}
.foot {
  margin-top: 14rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.hash {
  font-size: 20rpx;
  color: $text-4;
}
.link {
  font-size: 24rpx;
  color: $brand;
}
</style>
