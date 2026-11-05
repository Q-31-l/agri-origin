<template>
  <view class="trace">
    <!-- 搜索区 -->
    <view class="search-zone">
      <view class="search-box">
        <text class="icon">⌕</text>
        <input
          v-model="code"
          class="input"
          placeholder="输入包装上的溯源码，如 TC20260512001"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="query"
        />
        <text v-if="code" class="clear" @click="code = ''">×</text>
      </view>
      <view class="scan-btn" @click="onScan">
        <text class="scan-ico">⌗</text>
        <text class="scan-txt">扫码</text>
      </view>
    </view>

    <view v-if="!result && !loading" class="intro">
      <view class="intro-card">
        <text class="intro-title">全流程可信溯源</text>
        <text class="intro-desc">种植 · 采收 · 检测 · 运输 · 销售，每个环节的关键数据都已上链存证，任何改动都能被验出。</text>
        <view class="steps">
          <view v-for="(s, i) in steps" :key="i" class="step">
            <view class="step-dot">{{ i + 1 }}</view>
            <text class="step-txt">{{ s }}</text>
          </view>
        </view>
      </view>
      <view class="try">
        <text class="try-title">试试这些溯源码</text>
        <view class="try-list">
          <text v-for="c in samples" :key="c" class="try-tag mono" @click="quick(c)">{{ c }}</text>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading-wrap">
      <view class="skeleton sk-img"></view>
      <view class="skeleton sk-line"></view>
      <view class="skeleton sk-line short"></view>
    </view>

    <block v-if="result && !loading">
      <!-- 产品头 -->
      <view class="hero-card">
        <view class="hero-top">
          <view class="col flex1">
            <text class="product">{{ result.productName }}</text>
            <text v-if="result.variety" class="variety">{{ result.variety }}</text>
          </view>
          <status-tag :value="result.status" />
        </view>

        <view class="hero-meta">
          <view class="meta-item">
            <text class="mk">溯源码</text>
            <text class="mv mono">{{ result.traceCode }}</text>
          </view>
          <view class="meta-item">
            <text class="mk">产地</text>
            <text class="mv">{{ result.origin || result.farmName || '-' }}</text>
          </view>
          <view class="meta-item">
            <text class="mk">生产主体</text>
            <text class="mv">{{ result.farmerName || '-' }}</text>
          </view>
          <view class="meta-item">
            <text class="mk">数量</text>
            <text class="mv">{{ formatQty(result.quantity, result.unit) }}</text>
          </view>
        </view>

        <view v-if="result.abnormalFlag" class="abnormal-tip">
          <text class="tip-ico">!</text>
          <text class="tip-txt">该批次已被标记为异常，请谨慎购买</text>
        </view>
      </view>

      <!-- 可信度评分 -->
      <trust-score v-if="result.trust" :trust="result.trust" />

      <!-- 存证状态 -->
      <view class="chain-card">
        <view class="chain-head">
          <view class="row">
            <text class="chain-title">区块链存证</text>
            <chain-badge :status="result.chainStatus" :block-number="result.blockNumber" />
          </view>
          <text class="verify" @click="verify">一致性校验 ›</text>
        </view>
        <view v-if="result.chainTxHash" class="chain-row">
          <text class="ck">交易哈希</text>
          <text class="cv mono" @click="copy(result.chainTxHash)">{{ shortHash(result.chainTxHash) }}</text>
        </view>
        <view v-if="result.chainMessage" class="chain-msg">{{ result.chainMessage }}</view>
      </view>

      <!-- 质检 -->
      <view v-if="result.qualityResult || result.qualityReportNo" class="card-block">
        <view class="block-head">
          <text class="block-title">质量检测</text>
          <status-tag :value="result.qualityResult" type="quality" />
        </view>
        <view class="kv"><text class="k">报告编号</text><text class="v mono">{{ result.qualityReportNo || '-' }}</text></view>
        <view class="kv"><text class="k">检测机构</text><text class="v">{{ result.qualityOrg || '-' }}</text></view>
      </view>

      <!-- 时间轴 -->
      <view class="card-block">
        <view class="block-head">
          <text class="block-title">全流程履历</text>
          <text class="count">{{ (result.timeline || []).length }} 个节点</text>
        </view>
        <trace-timeline :items="result.timeline || []" />
      </view>

      <view class="foot-note">
        <text>数据来源：农安链溯源平台 · 链上存证不可篡改</text>
        <text v-if="result.chainEnabled === false" class="demo-note">当前为演示模式，未启用真实链节点</text>
      </view>
    </block>

    <view v-if="notFound" class="not-found">
      <empty-state text="未查询到该溯源码对应的批次" action-text="重新输入" @action="reset" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { traceQuery, traceVerify } from '@/api'
import StatusTag from '@/components/common/status-tag.vue'
import ChainBadge from '@/components/common/chain-badge.vue'
import TraceTimeline from '@/components/business/trace-timeline.vue'
import TrustScore from '@/components/business/trust-score.vue'
import EmptyState from '@/components/common/empty-state.vue'
import { scan, stopH5Scan, extractTraceCode } from '@/utils/scan'
import { formatQty, shortHash } from '@/utils/format'
import { toast } from '@/utils/ui'

const code = ref('')
const result = ref(null)
const loading = ref(false)
const notFound = ref(false)

const steps = ['种植记录', '采收登记', '质量检测', '物流运输', '销售去向']
const samples = ['TC20260512001', 'TC20260515002', 'TC20260601003']

onLoad((opt) => {
  const c = opt && (opt.code || opt.scene)
  if (c) {
    code.value = extractTraceCode(decodeURIComponent(c))
    query()
  }
})

onUnload(() => {
  // #ifdef H5
  stopH5Scan()
  // #endif
})

function quick(c) {
  code.value = c
  query()
}

function reset() {
  result.value = null
  notFound.value = false
  code.value = ''
}

async function query() {
  const c = (code.value || '').trim()
  if (!c) return toast('请输入溯源码')
  loading.value = true
  notFound.value = false
  try {
    const res = await traceQuery(c)
    result.value = res.data
  } catch (e) {
    result.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function onScan() {
  try {
    const raw = await scan()
    code.value = extractTraceCode(raw)
    query()
  } catch (e) {
    if (e && e.cancel) return
  }
}

async function verify() {
  if (!result.value) return
  try {
    const res = await traceVerify(result.value.traceCode)
    const d = res.data || {}
    const ok = d.consistent !== false && d.match !== false
    uni.showModal({
      title: ok ? '校验通过' : '校验异常',
      content: ok
        ? `链上摘要与链下数据一致\n区块高度：${d.blockNumber || result.value.blockNumber || '-'}`
        : `链上链下数据不一致，请谨慎采信\n${d.message || ''}`,
      showCancel: false
    })
  } catch (e) {
    /* 已提示 */
  }
}

function copy(v) {
  if (!v) return
  uni.setClipboardData({ data: String(v), success: () => uni.showToast({ title: '已复制', icon: 'none' }) })
}
</script>

<style lang="scss" scoped>
.trace {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 60rpx;
}
.search-zone {
  background: $brand-grad;
  padding: 24rpx $gap 48rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16rpx;
}
.search-box {
  flex: 1;
  height: 80rpx;
  background: #fff;
  border-radius: 40rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 24rpx;
}
.icon {
  font-size: 34rpx;
  color: $text-4;
  margin-right: 8rpx;
}
.input {
  flex: 1;
  font-size: 26rpx;
  color: $text-1;
}
.ph {
  color: $text-4;
  font-size: 24rpx;
}
.clear {
  font-size: 32rpx;
  color: $text-4;
}
.scan-btn {
  width: 104rpx;
  height: 80rpx;
  border-radius: 40rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.scan-ico {
  font-size: 30rpx;
  color: #fff;
  line-height: 1;
}
.scan-txt {
  font-size: 18rpx;
  color: #fff;
  margin-top: 2rpx;
}
.intro {
  padding: 0 $gap;
  margin-top: -24rpx;
}
.intro-card {
  background: #fff;
  border-radius: $radius;
  padding: 32rpx;
  box-shadow: $shadow-card;
}
.intro-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-1;
}
.intro-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $text-3;
  line-height: 1.7;
}
.steps {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 28rpx;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}
.step-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 24rpx;
  background: $brand-light;
  color: $brand;
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.step-txt {
  font-size: 20rpx;
  color: $text-3;
  margin-top: 8rpx;
}
.try {
  margin-top: 28rpx;
}
.try-title {
  font-size: 24rpx;
  color: $text-3;
}
.try-list {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 14rpx;
}
.try-tag {
  padding: 10rpx 20rpx;
  background: #fff;
  border-radius: 10rpx;
  font-size: 22rpx;
  color: $brand;
  border: 1px solid $border;
}
.loading-wrap {
  padding: 32rpx $gap;
}
.sk-img {
  height: 240rpx;
  border-radius: $radius;
}
.sk-line {
  height: 28rpx;
  margin-top: 24rpx;
}
.sk-line.short {
  width: 50%;
}
.hero-card {
  margin: -24rpx $gap 0;
  background: #fff;
  border-radius: $radius;
  padding: 28rpx;
  box-shadow: $shadow-card;
  position: relative;
}
.hero-top {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
}
.product {
  font-size: 40rpx;
  font-weight: 700;
  color: $text-1;
}
.variety {
  font-size: 24rpx;
  color: $text-3;
  margin-top: 6rpx;
}
.hero-meta {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 20rpx;
}
.meta-item {
  width: 50%;
  padding: 10rpx 0;
  display: flex;
  flex-direction: column;
}
.mk {
  font-size: 20rpx;
  color: $text-4;
}
.mv {
  font-size: 25rpx;
  color: $text-1;
  margin-top: 4rpx;
}
.abnormal-tip {
  margin-top: 16rpx;
  background: $danger-light;
  border-radius: 10rpx;
  padding: 14rpx 18rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.tip-ico {
  width: 32rpx;
  height: 32rpx;
  border-radius: 16rpx;
  background: $danger;
  color: #fff;
  font-size: 22rpx;
  text-align: center;
  line-height: 32rpx;
  margin-right: 12rpx;
}
.tip-txt {
  font-size: 24rpx;
  color: $danger;
}
.chain-card {
  margin: $gap $gap 0;
  background: #fff;
  border-radius: $radius;
  padding: 24rpx 28rpx;
  box-shadow: $shadow-card;
}
.chain-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.chain-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-right: 16rpx;
}
.verify {
  font-size: 24rpx;
  color: $brand;
}
.chain-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 14rpx;
}
.ck {
  font-size: 22rpx;
  color: $text-4;
  width: 110rpx;
}
.cv {
  font-size: 22rpx;
  color: $text-2;
  flex: 1;
}
.chain-msg {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $text-3;
}
.card-block {
  margin: $gap $gap 0;
  background: #fff;
  border-radius: $radius;
  padding: 24rpx 28rpx 28rpx;
  box-shadow: $shadow-card;
}
.block-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.block-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.count {
  font-size: 22rpx;
  color: $text-4;
}
.kv {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 0;
}
.kv .k {
  width: 140rpx;
  font-size: 24rpx;
  color: $text-4;
}
.kv .v {
  flex: 1;
  font-size: 24rpx;
  color: $text-1;
}
.foot-note {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.foot-note text {
  font-size: 20rpx;
  color: $text-4;
}
.demo-note {
  color: #b45309 !important;
}
.not-found {
  margin-top: 40rpx;
}
</style>
