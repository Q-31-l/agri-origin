<template>
  <view class="abnormal">
    <view class="summary">
      <view class="s-row">
        <text class="s-num">{{ total }}</text>
        <text class="s-unit">条异常处置单</text>
      </view>
      <text class="s-desc">异常批次已触发合约熔断，物流发运与销售写入将被拒绝</text>
    </view>

    <view class="list">
      <view v-for="r in list" :key="r.id" class="card">
        <view class="head">
          <view class="col flex1" @click="toDetail(r)">
            <text class="no mono">{{ r.batchNo }}</text>
            <text class="type">{{ abnormalTypeLabel(r.abnormalType) }}</text>
          </view>
          <text class="st" :style="statusStyle(r.status)">
            {{ abnormalStatusLabel(r.status) }}
          </text>
        </view>

        <text v-if="r.description" class="desc">{{ r.description }}</text>

        <view class="meta">
          <text class="m">处置人：{{ r.handlerName || '-' }}</text>
          <text class="m">{{ formatDateTime(r.handleTime) }}</text>
        </view>

        <view v-if="r.disposalMeasure" class="block">
          <text class="b-label">处置措施</text>
          <text class="b-text">{{ r.disposalMeasure }}</text>
        </view>
        <view v-if="r.disposalResult" class="block">
          <text class="b-label">处置结果</text>
          <text class="b-text">{{ r.disposalResult }}</text>
        </view>

        <view v-if="canDispose" class="acts">
          <text v-if="r.status !== 'DONE'" class="act" @click="openDispose(r)">处置登记</text>
          <text class="act ghost" @click="onClose(r)">关闭批次</text>
          <text class="act ghost" @click="toDetail(r)">查看详情</text>
        </view>
      </view>

      <empty-state v-if="!loading && !list.length" text="当前没有异常处置单" />
      <load-more v-if="list.length" :status="status" @retry="reload" />
    </view>

    <!-- 处置弹窗 -->
    <view v-if="show" class="mask" @click="show = false">
      <view class="dialog" @click.stop>
        <text class="d-title">异常处置</text>
        <text class="d-sub">{{ current.batchNo }}</text>

        <text class="d-label">异常类型</text>
        <picker mode="selector" :range="typeLabels" @change="onTypeChange">
          <view class="d-input picker">
            <text>{{ abnormalTypeLabel(form.abnormalType) }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>

        <text class="d-label">处置措施</text>
        <textarea
          v-model="form.disposalMeasure"
          class="d-textarea"
          placeholder="如 全部下架并就地无害化处理"
          placeholder-class="ph"></textarea>

        <text class="d-label">处置结果</text>
        <textarea
          v-model="form.disposalResult"
          class="d-textarea small"
          placeholder="如 已销毁 120kg，未流入市场"
          placeholder-class="ph"></textarea>

        <text class="d-label">实证材料（图片 / 视频，哈希并入上链）</text>
        <evidence-uploader
          ref="uploader"
          v-model="form.evidences"
          biz-type="DISPOSAL"
          :max="6" />

        <view class="d-actions">
          <view class="d-btn ghost" @click="show = false">取消</view>
          <view class="d-btn" @click="submitDispose">提交处置</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import EmptyState from '@/components/common/empty-state.vue'
import LoadMore from '@/components/common/load-more.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useList } from '@/composables/useList'
import { abnormalPage, disposeAbnormal, closeBatch } from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import {
  ABNORMAL_STATUS,
  ABNORMAL_TYPE_OPTIONS,
  abnormalStatusLabel,
  abnormalTypeLabel
} from '@/config/constants'
import { formatDateTime } from '@/utils/format'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { confirm, toast } from '@/utils/ui'

const store = useUserStore()
const cache = useCacheStore()
const canDispose = computed(() => store.hasPerm('abnormal:dispose'))

const show = ref(false)
const current = ref({})
const uploader = ref(null)
const form = reactive({ abnormalType: 'OTHER', disposalMeasure: '', disposalResult: '', evidences: [] })

const typeLabels = ABNORMAL_TYPE_OPTIONS.map((t) => t.label)

/** 异常处置单分页（后端 /abnormal/page，参数为 batchNo / status） */
const { list, total, loading, status, reload, loadMore } = useList(abnormalPage, {
  pageSize: 10,
  filters: () => ({})
})

function statusStyle(s) {
  const c = ABNORMAL_STATUS[s] || { color: '#64748b', bg: '#f1f2f4' }
  return { color: c.color, background: c.bg }
}

function toDetail(r) {
  nav(ROUTES.BATCH_DETAIL, { id: r.batchId })
}

function onTypeChange(e) {
  form.abnormalType = ABNORMAL_TYPE_OPTIONS[e.detail.value].value
}

function openDispose(r) {
  current.value = r
  form.abnormalType = r.abnormalType || 'OTHER'
  form.disposalMeasure = r.disposalMeasure || ''
  form.disposalResult = r.disposalResult || ''
  form.evidences = []
  show.value = true
}

async function submitDispose() {
  if (!form.disposalMeasure.trim()) return toast('请填写处置措施')
  if (!form.disposalResult.trim()) return toast('请填写处置结果')
  if (uploader.value && uploader.value.isUploading()) return toast('实证材料仍在上传，请稍候')
  try {
    await disposeAbnormal(current.value.id, {
      abnormalType: form.abnormalType,
      disposalMeasure: form.disposalMeasure,
      disposalResult: form.disposalResult,
      evidenceIds: form.evidences.map((f) => f.id)
    })
    uni.showToast({ title: '处置已登记', icon: 'success' })
    show.value = false
    cache.invalidate('abnormal', 'batch')
    reload()
  } catch (e) {
    /* 已提示 */
  }
}

async function onClose(r) {
  if (!r.batchId) return toast('缺少批次信息')
  const ok = await confirm(`确定关闭批次 ${r.batchNo} 吗？关闭后不可再操作。`, '关闭批次')
  if (!ok) return
  try {
    await closeBatch(r.batchId)
    uni.showToast({ title: '批次已关闭', icon: 'success' })
    cache.invalidate('abnormal', 'batch')
    reload()
  } catch (e) {
    /* 已提示 */
  }
}

onShow(() => reload())
onPullDownRefresh(() => reload())
onReachBottom(() => loadMore())
</script>

<style lang="scss" scoped>
.abnormal {
  min-height: 100vh;
  background: $bg-page;
}
.summary {
  background: linear-gradient(135deg, #b91c1c 0%, #d93026 100%);
  padding: 36rpx 32rpx 44rpx;
  display: flex;
  flex-direction: column;
}
.s-row {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: 10rpx;
}
.s-num {
  font-size: 64rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1.1;
}
.s-unit {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}
.s-desc {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.6;
}
.list {
  padding: 20rpx 24rpx 40rpx;
  margin-top: -22rpx;
}
.card {
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  margin-bottom: $gap-sm;
  box-shadow: $shadow-card;
  border-left: 6rpx solid $danger;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}
.col {
  display: flex;
  flex-direction: column;
}
.flex1 {
  flex: 1;
  min-width: 0;
}
.no {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-1;
}
.type {
  font-size: 22rpx;
  color: $danger;
  margin-top: 6rpx;
}
.st {
  font-size: 20rpx;
  border-radius: 8rpx;
  padding: 4rpx 14rpx;
  flex-shrink: 0;
}
.desc {
  display: block;
  font-size: 23rpx;
  color: $text-2;
  line-height: 1.6;
  margin-top: 14rpx;
}
.meta {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 14rpx;
}
.m {
  font-size: 21rpx;
  color: $text-3;
}
.block {
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid $border;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.b-label {
  font-size: 20rpx;
  color: $text-4;
}
.b-text {
  font-size: 22rpx;
  color: $text-2;
  line-height: 1.6;
}
.acts {
  display: flex;
  flex-direction: row;
  gap: 12rpx;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $border;
}
.act {
  padding: 10rpx 22rpx;
  border-radius: 24rpx;
  background: $danger;
  color: #fff;
  font-size: 22rpx;
}
.act.ghost {
  background: #f4f6f8;
  color: $text-2;
}
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dialog {
  width: 620rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx;
  max-height: 80vh;
  overflow-y: auto;
}
.d-title {
  font-size: 32rpx;
  font-weight: 600;
  display: block;
}
.d-sub {
  font-size: 22rpx;
  color: $text-3;
  margin-top: 6rpx;
  display: block;
}
.d-label {
  display: block;
  font-size: 24rpx;
  color: $text-2;
  margin-top: 20rpx;
}
.d-input {
  height: 76rpx;
  background: #f8fafb;
  border-radius: 10rpx;
  padding: 0 16rpx;
  font-size: 26rpx;
  margin-top: 8rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.picker .arrow {
  color: $text-4;
  font-size: 26rpx;
}
.d-textarea {
  width: 100%;
  height: 130rpx;
  background: #f8fafb;
  border-radius: 10rpx;
  padding: 14rpx 16rpx;
  font-size: 26rpx;
  margin-top: 8rpx;
  box-sizing: border-box;
}
.d-textarea.small {
  height: 100rpx;
}
.ph {
  color: $text-4;
  font-size: 24rpx;
}
.d-actions {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  margin-top: 28rpx;
}
.d-btn {
  flex: 1;
  height: 84rpx;
  border-radius: 42rpx;
  background: $brand;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
.d-btn.ghost {
  background: #eef1f4;
  color: $text-2;
}
</style>
