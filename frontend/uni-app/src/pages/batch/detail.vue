<template>
  <view class="detail">
    <view v-if="loading" class="loading-wrap">
      <view class="skeleton s1"></view>
      <view class="skeleton s2"></view>
      <view class="skeleton s3"></view>
    </view>

    <block v-else-if="batch.id">
      <!-- 头部信息 -->
      <view class="hero">
        <view class="hero-row">
          <view class="col flex1">
            <text class="batch-no mono">{{ batch.batchNo }}</text>
            <text class="product">{{ batch.productName }}<text v-if="batch.variety" class="variety"> · {{ batch.variety }}</text></text>
          </view>
          <view class="col tags">
            <status-tag :value="batch.status" />
            <status-tag v-if="batch.auditStatus" :value="batch.auditStatus" type="audit" />
          </view>
        </view>

        <view class="hero-grid">
          <view class="g-item"><text class="gk">溯源码</text><text class="gv mono">{{ batch.traceCode }}</text></view>
          <view class="g-item"><text class="gk">农场</text><text class="gv">{{ batch.farmName || '-' }}</text></view>
          <view class="g-item"><text class="gk">农户</text><text class="gv">{{ batch.farmerName || '-' }}</text></view>
          <view class="g-item"><text class="gk">产量</text><text class="gv">{{ formatQty(batch.quantity, batch.unit) }}</text></view>
          <view class="g-item"><text class="gk">种植日期</text><text class="gv">{{ formatDate(batch.plantingDate) }}</text></view>
          <view class="g-item"><text class="gk">采收日期</text><text class="gv">{{ formatDate(batch.harvestDate) }}</text></view>
        </view>

        <view v-if="batch.abnormalFlag" class="abnormal">
          <text class="a-tag">异常</text>
          <text class="a-text">该批次已被标记异常，物流与销售写入已被熔断</text>
        </view>
      </view>

      <!-- 存证 -->
      <view class="chain-bar">
        <view class="row">
          <text class="c-title">链上存证</text>
          <chain-badge :status="batch.chainStatus" :block-number="batch.blockNumber" />
        </view>
        <view class="row gap">
          <text class="verify-btn" @click="verify">一致性校验</text>
          <text class="copy-btn" @click="copy(batch.chainTxHash)">复制交易哈希</text>
        </view>
        <text v-if="batch.chainTxHash" class="tx mono">{{ shortHash(batch.chainTxHash) }}</text>
      </view>

      <!-- 操作 -->
      <view v-if="actions.length" class="actions">
        <view v-for="a in actions" :key="a.key" class="act" @click="a.handler">
          <text class="act-icon" :style="{ color: a.color }">{{ a.icon }}</text>
          <text class="act-title">{{ a.title }}</text>
        </view>
      </view>

      <!-- 二维码 -->
      <view class="qr-card">
        <qr-code :code="batch.traceCode" label="消费者扫码查看全流程履历" />
        <view class="qr-side">
          <text class="qr-title">产品溯源二维码</text>
          <text class="qr-desc">包装上印制此二维码，消费者扫码即可查看种植、采收、检测、运输、销售的全流程记录与链上存证。</text>
          <view class="qr-actions">
            <text class="qr-btn" @click="copy(batch.traceCode)">复制溯源码</text>
            <text class="qr-btn" @click="toTrace">查看溯源页</text>
          </view>
        </view>
      </view>

      <!-- 质检报告 -->
      <view v-if="qualities.length" class="section">
        <view class="sec-head">
          <text class="sec-title">质检报告</text>
          <text class="sec-count">{{ qualities.length }} 份</text>
        </view>
        <quality-report v-for="q in qualities" :key="q.id" :report="q" />
      </view>

      <!-- 时间轴 -->
      <view class="section">
        <view class="sec-head">
          <text class="sec-title">全流程履历</text>
          <text class="sec-count">{{ (timeline || []).length }} 个节点</text>
        </view>
        <trace-timeline :items="timeline" />
      </view>

      <!-- 农事记录 -->
      <view v-if="plantings.length" class="section">
        <view class="sec-head">
          <text class="sec-title">农事记录</text>
          <text class="sec-count">{{ plantings.length }} 条</text>
        </view>
        <view v-for="p in plantings" :key="p.id" class="rec">
          <view class="rec-head">
            <text class="rec-type">{{ plantingTypeLabel(p.recordType) }}</text>
            <text class="rec-date">{{ formatDate(p.operationDate) }}</text>
          </view>
          <text class="rec-content">{{ p.content }}</text>
          <text v-if="p.location" class="rec-loc">作业地点：{{ p.location }}</text>
          <view class="rec-meta">
            <text v-if="p.materialName" class="rec-tag">用药：{{ p.materialName }} {{ p.dosage || '' }}</text>
            <status-tag v-if="p.auditStatus" :value="p.auditStatus" type="audit" />
          </view>
        </view>
      </view>

      <!-- 物流与销售 -->
      <view v-if="logistics.length || sales.length" class="section">
        <view class="sec-head"><text class="sec-title">流通记录</text></view>
        <view v-for="l in logistics" :key="'l' + l.id" class="rec">
          <view class="rec-head">
            <text class="rec-type">物流 {{ l.logisticsNo }}</text>
            <text class="rec-date">{{ formatDateTime(l.departTime) }}</text>
          </view>
          <text class="rec-content">{{ l.fromOrg || '-' }} → {{ l.toOrg || '-' }}</text>
          <text class="rec-meta-line">{{ l.vehicleNo || '' }} {{ l.driverName || '' }} {{ l.temperature ? '· 温度 ' + l.temperature : '' }}</text>
        </view>
        <view v-for="s in sales" :key="'s' + s.id" class="rec">
          <view class="rec-head">
            <text class="rec-type">销售 · {{ s.marketName }}</text>
            <text class="rec-date">{{ formatDate(s.salesDate) }}</text>
          </view>
          <text class="rec-content">{{ formatQty(s.quantity, s.unit) }} × ￥{{ s.price }}/{{ s.unit }}</text>
          <text v-if="s.buyerName" class="rec-meta-line">采购方：{{ s.buyerName }}</text>
        </view>
      </view>

      <view class="bottom-space"></view>
    </block>

    <empty-state v-else text="批次不存在或无权访问" />

    <!-- 审核弹窗 -->
    <view v-if="showAudit" class="mask" @click="showAudit = false">
      <view class="dialog" @click.stop>
        <text class="d-title">审核批次</text>
        <text class="d-sub">{{ batch.batchNo }} · {{ batch.productName }}</text>
        <textarea v-model="auditRemark" class="d-textarea" placeholder="审核意见（驳回时必填）" placeholder-class="ph"></textarea>
        <view class="d-actions">
          <view class="d-btn reject" @click="doAudit('REJECTED')">驳回</view>
          <view class="d-btn pass" @click="doAudit('APPROVED')">通过</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { batchDetail, verifyBatch, auditBatch, closeBatch } from '@/api'
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import TraceTimeline from '@/components/business/trace-timeline.vue'
import QualityReport from '@/components/business/quality-report.vue'
import QrCode from '@/components/business/qr-code.vue'
import EmptyState from '@/components/common/empty-state.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { formatDate, formatDateTime, formatQty, shortHash } from '@/utils/format'
import { plantingTypeLabel } from '@/config/constants'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { confirm, toast } from '@/utils/ui'

const store = useUserStore()
const cache = useCacheStore()

const batchId = ref('')
const loading = ref(true)
const detail = ref({})
const batch = computed(() => detail.value.batch || {})
const timeline = computed(() => detail.value.timeline || [])
const plantings = computed(() => detail.value.plantings || [])
const qualities = computed(() => detail.value.qualities || [])
const logistics = computed(() => detail.value.logistics || [])
const sales = computed(() => detail.value.sales || [])

const showAudit = ref(false)
const auditRemark = ref('')

const actions = computed(() => {
  const b = batch.value
  const list = []
  if (!b.id) return list

  if (b.auditStatus === 'PENDING' && store.hasPerm('batch:audit')) {
    list.push({ key: 'audit', title: '审核', icon: '✓', color: '#1f7a4d', handler: () => (showAudit.value = true) })
  }
  if (['PLANTING', 'HARVESTED'].includes(b.status) && store.hasPerm('planting:create')) {
    list.push({ key: 'planting', title: '农事', icon: '❀', color: '#2563eb', handler: () => nav(ROUTES.PLANTING_FORM, { batchId: b.id }) })
  }
  if (b.status === 'PLANTING' && store.hasPerm('harvest:create')) {
    list.push({ key: 'harvest', title: '采收', icon: '✿', color: '#b45309', handler: () => nav(ROUTES.HARVEST_FORM, { batchId: b.id }) })
  }
  if (b.status === 'HARVESTED' && store.hasPerm('quality:create')) {
    list.push({ key: 'quality', title: '质检', icon: '✓', color: '#7c3aed', handler: () => nav(ROUTES.QUALITY_FORM, { batchId: b.id }) })
  }
  if (['QUALIFIED'].includes(b.status) && store.hasPerm('logistics:create')) {
    list.push({ key: 'logistics', title: '发运', icon: '⇥', color: '#0891b2', handler: () => nav(ROUTES.LOGISTICS_FORM, { batchId: b.id }) })
  }
  if (['QUALIFIED', 'IN_TRANSIT', 'ON_SALE'].includes(b.status) && store.hasPerm('sales:create')) {
    list.push({ key: 'sales', title: '销售', icon: '¥', color: '#c2410c', handler: () => nav(ROUTES.SALES_FORM, { batchId: b.id }) })
  }
  if (b.status === 'ABNORMAL' && store.hasPerm('abnormal:dispose')) {
    list.push({ key: 'dispose', title: '处置', icon: '!', color: '#d93026', handler: () => nav(ROUTES.ABNORMAL) })
  }
  if (b.auditStatus === 'PENDING' && store.hasPerm('batch:update')) {
    list.push({ key: 'edit', title: '编辑', icon: '✎', color: '#64748b', handler: () => nav(ROUTES.BATCH_EDIT, { id: b.id }) })
  }
  return list
})

async function load() {
  if (!batchId.value) return
  loading.value = true
  try {
    const res = await batchDetail(batchId.value)
    detail.value = res.data || {}
  } catch (e) {
    detail.value = {}
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

onLoad((opt) => {
  batchId.value = opt.id
  load()
})

onShow(() => {
  if (cache.isDirty('batch:detail') && !loading.value) load()
})

onPullDownRefresh(() => load())

async function doAudit(result) {
  if (result === 'REJECTED' && !auditRemark.value.trim()) return toast('驳回时请填写审核意见')
  try {
    await auditBatch(batch.value.id, { auditStatus: result, auditRemark: auditRemark.value.trim() })
    uni.showToast({ title: result === 'APPROVED' ? '审核通过' : '已驳回', icon: 'success' })
    showAudit.value = false
    auditRemark.value = ''
    cache.invalidate('batch:list', 'batch:detail')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

async function verify() {
  try {
    const res = await verifyBatch(batch.value.id)
    const d = res.data || {}
    uni.showModal({
      title: d.passed ? '校验通过' : '校验未通过',
      content: `${d.message || ''}\n比对记录：${d.matchedRecords || 0}/${d.totalRecords || 0}`,
      showCancel: false
    })
  } catch (e) {
    /* 已提示 */
  }
}

function copy(v) {
  if (!v) return toast('暂无可复制内容')
  uni.setClipboardData({ data: String(v), success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}

function toTrace() {
  nav(ROUTES.TRACE, { code: batch.value.traceCode })
}
</script>

<style lang="scss" scoped>
.detail {
  min-height: 100vh;
  background: $bg-page;
}
.loading-wrap {
  padding: 24rpx;
}
.skeleton {
  border-radius: $radius;
  background: #e9edf2;
  margin-bottom: 20rpx;
}
.s1 { height: 320rpx; }
.s2 { height: 120rpx; }
.s3 { height: 200rpx; }
.hero {
  background: $brand-grad;
  padding: 32rpx 28rpx 40rpx;
}
.hero-row {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
}
.batch-no {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.8);
}
.product {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  margin-top: 6rpx;
}
.variety {
  font-size: 26rpx;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.8);
}
.tags {
  align-items: flex-end;
  gap: 8rpx;
}
.hero-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 24rpx;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12rpx;
  padding: 8rpx 16rpx;
}
.g-item {
  width: 50%;
  padding: 10rpx 0;
  display: flex;
  flex-direction: column;
}
.gk {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.65);
}
.gv {
  font-size: 25rpx;
  color: #fff;
  margin-top: 2rpx;
}
.abnormal {
  margin-top: 18rpx;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 10rpx;
  padding: 14rpx 16rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.a-tag {
  background: $danger;
  color: #fff;
  font-size: 20rpx;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  margin-right: 12rpx;
}
.a-text {
  flex: 1;
  font-size: 22rpx;
  color: $danger;
}
.chain-bar {
  margin: -20rpx 24rpx 0;
  background: #fff;
  border-radius: $radius;
  padding: 20rpx 24rpx;
  box-shadow: $shadow-card;
}
.chain-bar .row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.chain-bar .gap {
  margin-top: 14rpx;
  justify-content: flex-start;
  gap: 16rpx;
}
.c-title {
  font-size: 26rpx;
  font-weight: 600;
  margin-right: 14rpx;
}
.verify-btn {
  font-size: 24rpx;
  color: $brand;
  background: $brand-light;
  padding: 8rpx 22rpx;
  border-radius: 24rpx;
}
.copy-btn {
  font-size: 24rpx;
  color: $text-3;
  background: #f4f6f8;
  padding: 8rpx 22rpx;
  border-radius: 24rpx;
}
.tx {
  display: block;
  margin-top: 12rpx;
  font-size: 20rpx;
  color: $text-4;
}
.actions {
  margin: 24rpx;
  background: #fff;
  border-radius: $radius;
  padding: 12rpx;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  box-shadow: $shadow-card;
}
.act {
  width: 25%;
  padding: 16rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.act-icon {
  font-size: 38rpx;
  line-height: 1;
}
.act-title {
  font-size: 22rpx;
  color: $text-2;
  margin-top: 10rpx;
}
.qr-card {
  margin: 0 24rpx 24rpx;
  background: #fff;
  border-radius: $radius;
  padding: 28rpx;
  display: flex;
  flex-direction: row;
  box-shadow: $shadow-card;
}
.qr-side {
  flex: 1;
  margin-left: 24rpx;
}
.qr-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $text-1;
}
.qr-desc {
  display: block;
  font-size: 22rpx;
  color: $text-3;
  line-height: 1.6;
  margin-top: 10rpx;
}
.qr-actions {
  display: flex;
  flex-direction: row;
  gap: 12rpx;
  margin-top: 18rpx;
}
.qr-btn {
  font-size: 22rpx;
  color: $brand;
  background: $brand-light;
  padding: 8rpx 18rpx;
  border-radius: 8rpx;
}
.section {
  margin: 0 24rpx 24rpx;
  background: #fff;
  border-radius: $radius;
  padding: 24rpx;
  box-shadow: $shadow-card;
}
.sec-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.sec-title {
  font-size: 30rpx;
  font-weight: 600;
}
.sec-count {
  font-size: 22rpx;
  color: $text-4;
}
.rec {
  padding: 16rpx 0;
  border-bottom: 1px solid $border;
}
.rec:last-child {
  border-bottom: none;
}
.rec-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.rec-type {
  font-size: 26rpx;
  font-weight: 500;
  color: $text-1;
}
.rec-date {
  font-size: 22rpx;
  color: $text-4;
}
.rec-content {
  display: block;
  font-size: 24rpx;
  color: $text-2;
  margin-top: 8rpx;
  line-height: 1.6;
}
.rec-loc {
  display: block;
  font-size: 21rpx;
  color: $text-4;
  margin-top: 6rpx;
  line-height: 1.5;
}
.rec-meta {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
  margin-top: 10rpx;
}
.rec-tag {
  font-size: 20rpx;
  color: $text-3;
  background: #f4f6f8;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
}
.rec-meta-line {
  display: block;
  font-size: 22rpx;
  color: $text-3;
  margin-top: 8rpx;
}
.bottom-space {
  height: 40rpx;
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
  width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 36rpx 32rpx 24rpx;
}
.d-title {
  font-size: 32rpx;
  font-weight: 600;
  display: block;
}
.d-sub {
  font-size: 24rpx;
  color: $text-3;
  margin-top: 8rpx;
  display: block;
}
.d-textarea {
  width: 100%;
  height: 160rpx;
  background: #f8fafb;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-top: 20rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
}
.d-actions {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  margin-top: 24rpx;
}
.d-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
}
.d-btn.reject {
  background: $danger-light;
  color: $danger;
}
.d-btn.pass {
  background: $brand;
  color: #fff;
}
</style>
