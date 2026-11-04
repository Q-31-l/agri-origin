<template>
  <div class="page-plain trace">
    <!-- 顶部搜索区 -->
    <div class="hero" :class="{ compact: !!data }">
      <div class="hero-top">
        <van-icon v-if="data" name="arrow-left" class="back" @click="reset" />
        <div class="brand">
          <div class="brand-logo"><van-icon name="flower-o" /></div>
          <div>
            <div class="brand-name">农产品质量安全溯源</div>
            <div class="brand-sub">FISCO-BCOS 联盟链存证 · 数据不可篡改</div>
          </div>
        </div>
      </div>

      <div class="search">
        <van-icon name="search" class="s-icon" />
        <input
          v-model="code"
          class="s-input"
          placeholder="输入溯源码 / 批次号，如 TR2025..."
          confirm-type="search"
          @keyup.enter="doQuery"
        />
        <div class="s-btn" @click="doQuery">查询</div>
      </div>

      <div class="hint">
        <van-icon name="scan" />
        <span>扫描产品包装上的溯源二维码可直接查看全流程履历</span>
      </div>
    </div>

    <van-loading v-if="loading" class="loading" size="22" vertical>正在核验链上数据…</van-loading>

    <!-- 查询结果 -->
    <template v-if="data && !loading">
      <!-- 产品概览 -->
      <div class="product-card">
        <div class="p-status" :class="data.abnormalFlag === 1 ? 'bad' : 'ok'">
          <van-icon :name="data.abnormalFlag === 1 ? 'warning-o' : 'passed'" />
          {{ data.abnormalFlag === 1 ? '该批次已被管控' : '批次状态正常' }}
        </div>

        <div class="p-name">{{ data.productName }}</div>
        <div class="p-variety" v-if="data.variety">{{ data.variety }}</div>

        <div class="p-grid">
          <div class="p-item">
            <div class="p-label">产地</div>
            <div class="p-value">{{ data.origin || '—' }}</div>
          </div>
          <div class="p-item">
            <div class="p-label">生产农户</div>
            <div class="p-value">{{ data.farmerName || '—' }}</div>
          </div>
          <div class="p-item">
            <div class="p-label">种植日期</div>
            <div class="p-value">{{ data.plantingDate || '—' }}</div>
          </div>
          <div class="p-item">
            <div class="p-label">采收日期</div>
            <div class="p-value">{{ data.harvestDate || '—' }}</div>
          </div>
          <div class="p-item">
            <div class="p-label">批次数量</div>
            <div class="p-value">{{ data.quantity || '—' }}{{ data.unit || '' }}</div>
          </div>
          <div class="p-item">
            <div class="p-label">当前状态</div>
            <div class="p-value">{{ statusLabel(data.status) }}</div>
          </div>
        </div>

        <div class="p-codes">
          <div class="code-row">
            <span class="c-label">溯源编号</span>
            <span class="mono">{{ data.traceCode }}</span>
          </div>
          <div class="code-row">
            <span class="c-label">批次编号</span>
            <span class="mono">{{ data.batchNo }}</span>
          </div>
        </div>
      </div>

      <!-- 质检结论 -->
      <div class="card quality" v-if="data.qualityResult">
        <div class="q-left">
          <van-icon :name="data.qualityResult === 'PASS' ? 'passed' : 'close'" />
        </div>
        <div class="q-body">
          <div class="q-title">
            质量检测{{ data.qualityResult === 'PASS' ? '合格' : '不合格' }}
          </div>
          <div class="q-sub">
            {{ data.qualityOrg || '检测机构' }}<span v-if="data.qualityReportNo"> · 报告号 {{ data.qualityReportNo }}</span>
          </div>
        </div>
      </div>

      <!-- 存证状态 -->
      <div class="card chain-card">
        <div class="chain-head">
          <div class="chain-title">
            <span class="dot" :class="verifyState"></span>
            链上存证校验
          </div>
          <van-button size="mini" round plain type="primary" :loading="verifying" @click="doVerify">
            重新校验
          </van-button>
        </div>

        <div v-if="verify" class="verify-result" :class="verify.passed ? 'ok' : 'bad'">
          <div class="v-main">
            <van-icon :name="verify.passed ? 'shield-o' : 'warning-o'" />
            {{ verify.passed ? '链上链下数据一致，未被篡改' : '校验未通过，数据存在异常' }}
          </div>
          <div class="v-sub">{{ verify.message }}</div>
          <div class="v-nums">
            <span>存证记录 {{ verify.totalRecords }} 条</span>
            <span>校验通过 {{ verify.matchedRecords }} 条</span>
          </div>
        </div>

        <div v-else class="verify-note">
          {{ data.chainEnabled ? '点击「重新校验」比对链上摘要' : data.chainMessage || '当前为演示模式，未接入区块链网络' }}
        </div>

        <div class="chain-meta" v-if="data.chainTxHash">
          <div class="meta-row">
            <span class="m-label">交易哈希</span>
            <span class="mono">{{ data.chainTxHash }}</span>
          </div>
          <div class="meta-row" v-if="data.blockNumber">
            <span class="m-label">区块高度</span>
            <span class="mono">{{ data.blockNumber }}</span>
          </div>
        </div>
      </div>

      <!-- 全流程履历 -->
      <div class="section-title">全流程履历</div>
      <div class="timeline">
        <div v-for="(t, i) in data.timeline" :key="i" class="tl-item" :class="t.type">
          <div class="tl-axis">
            <div class="tl-dot"><van-icon :name="iconOf(t.type)" /></div>
            <div class="tl-line" v-if="i < data.timeline.length - 1"></div>
          </div>
          <div class="tl-body">
            <div class="tl-head">
              <span class="tl-title">{{ t.title }}</span>
              <span class="tl-time">{{ t.time }}</span>
            </div>
            <div class="tl-detail" v-if="t.detail">{{ t.detail }}</div>
            <div class="tl-operator" v-if="t.operator">
              <van-icon name="user-o" /> {{ t.operator }}
            </div>
            <div class="tl-hash" v-if="t.dataHash">
              <span class="h-label">数据摘要</span>
              <span class="mono">{{ t.dataHash }}</span>
            </div>
            <div class="tl-chain" v-if="t.chainStatus">
              <span class="chip" :class="t.chainStatus">{{ chainLabel(t.chainStatus) }}</span>
              <span v-if="t.txHash" class="mono tx">{{ short(t.txHash) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="foot-note">
        本页面数据由联盟链节点与业务数据库双向校验生成，任何对历史记录的修改都会导致摘要不一致。
      </div>
    </template>

    <van-empty v-else-if="!loading && searched" description="未查询到该批次，请核对溯源码" />

    <!-- 未查询时的说明 -->
    <div v-if="!data && !loading && !searched" class="intro">
      <div class="intro-title">你能查到什么</div>
      <div class="intro-grid">
        <div class="intro-item" v-for="it in intro" :key="it.title">
          <div class="i-icon" :style="{ background: it.bg, color: it.color }">
            <van-icon :name="it.icon" />
          </div>
          <div class="i-title">{{ it.title }}</div>
          <div class="i-desc">{{ it.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { traceQuery, traceVerify } from '@/api'

const route = useRoute()
const router = useRouter()

const code = ref('')
const data = ref(null)
const verify = ref(null)
const loading = ref(false)
const verifying = ref(false)
const searched = ref(false)

const intro = [
  { title: '种植履历', desc: '农事作业全程留痕', icon: 'seedling-o', bg: '#eaf7f0', color: '#2f9e63' },
  { title: '质检报告', desc: '检测结论上链存证', icon: 'certificate', bg: '#eaf1ff', color: '#3b82f6' },
  { title: '物流轨迹', desc: '交接环节可追溯', icon: 'logistics', bg: '#fff5e8', color: '#d99a2b' },
  { title: '销售终端', desc: '流通去向可核查', icon: 'shop-o', bg: '#f3edff', color: '#8b5cf6' }
]

const statusLabel = (s) =>
  ({
    PLANTING: '种植中',
    HARVESTED: '已采收',
    QUALIFIED: '质检合格',
    IN_TRANSIT: '运输中',
    ON_SALE: '在售',
    SOLD: '已售出',
    ABNORMAL: '异常管控',
    CLOSED: '已结案'
  }[s] || s)

const chainLabel = (s) => ({ SUCCESS: '已上链', FAILED: '上链失败', SKIPPED: '演示模式' }[s] || '未上链')

const iconOf = (t) =>
  ({
    BATCH: 'label-o',
    PLANTING: 'seedling-o',
    HARVEST: 'flower-o',
    QUALITY: 'certificate',
    LOGISTICS: 'logistics',
    SALES: 'shop-o',
    ABNORMAL: 'warning-o',
    DISPOSAL: 'shield-o',
    CORRECTION: 'edit'
  }[t] || 'clock-o')

const short = (s) => (s && s.length > 18 ? `${s.slice(0, 10)}…${s.slice(-6)}` : s)

const verifyState = computed(() => {
  if (!verify.value) return 'idle'
  return verify.value.passed ? 'ok' : 'bad'
})

const doQuery = async () => {
  const c = (code.value || '').trim()
  if (!c) return showToast('请输入溯源码或批次号')
  loading.value = true
  searched.value = true
  data.value = null
  verify.value = null
  try {
    const res = await traceQuery(c)
    data.value = res.data
    doVerify()
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}

const doVerify = async () => {
  if (!data.value) return
  verifying.value = true
  try {
    const res = await traceVerify(data.value.traceCode || data.value.batchNo)
    verify.value = res.data
  } catch (e) {
    verify.value = null
  } finally {
    verifying.value = false
  }
}

const reset = () => {
  data.value = null
  verify.value = null
  searched.value = false
  code.value = ''
  router.replace({ path: route.path })
}

onMounted(() => {
  const q = route.query.code
  if (q) {
    code.value = String(q)
    doQuery()
  }
})
</script>

<style scoped>
.trace {
  padding-bottom: calc(80px + env(safe-area-inset-bottom));
}

.hero {
  background: linear-gradient(160deg, #123626 0%, #1f5c3e 60%, #2f8556 100%);
  padding: 22px 18px 26px;
  color: #eaf6ef;
  border-radius: 0 0 22px 22px;
}

.hero.compact {
  padding-bottom: 20px;
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.back {
  font-size: 18px;
  color: #eaf6ef;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
}

.brand-sub {
  font-size: 10px;
  color: rgba(207, 227, 216, 0.7);
  margin-top: 3px;
}

.search {
  display: flex;
  align-items: center;
  margin-top: 20px;
  background: #fff;
  border-radius: 999px;
  padding: 5px 6px 5px 14px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.12);
}

.s-icon {
  color: #9aa8b6;
  font-size: 16px;
}

.s-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: #1f2d3d;
  padding: 8px 8px;
}

.s-input::placeholder {
  color: #a8b4c0;
  font-size: 12px;
}

.s-btn {
  background: linear-gradient(135deg, #2f9e63, #1f7a4c);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 18px;
  border-radius: 999px;
}

.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: rgba(207, 227, 216, 0.7);
  margin-top: 12px;
  padding-left: 4px;
}

.loading {
  padding: 40px 0;
}

.product-card {
  background: #fff;
  border-radius: 16px;
  margin: -14px 14px 0;
  padding: 16px;
  position: relative;
  box-shadow: 0 8px 24px rgba(20, 60, 44, 0.08);
}

.p-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  padding: 3px 9px;
  border-radius: 999px;
}

.p-status.ok {
  background: #eaf7f0;
  color: #1f7a4c;
}

.p-status.bad {
  background: #fdeeee;
  color: #e05252;
}

.p-name {
  font-size: 21px;
  font-weight: 700;
  margin-top: 10px;
  letter-spacing: 0.5px;
}

.p-variety {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.p-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #eef2f5;
}

.p-label {
  font-size: 10px;
  color: #96a3b1;
}

.p-value {
  font-size: 13px;
  font-weight: 500;
  color: #2b3b4a;
  margin-top: 4px;
  word-break: break-all;
}

.p-codes {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #eef2f5;
}

.code-row {
  display: flex;
  gap: 10px;
  font-size: 11px;
  line-height: 1.9;
}

.c-label {
  color: #96a3b1;
  flex-shrink: 0;
  width: 56px;
}

.quality {
  display: flex;
  align-items: center;
  gap: 12px;
}

.q-left {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #eaf7f0;
  color: #2f9e63;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  flex-shrink: 0;
}

.q-title {
  font-size: 14px;
  font-weight: 600;
}

.q-sub {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.6;
}

.chain-card {
  padding: 16px;
}

.chain-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chain-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 7px;
}

.chain-title .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c3ccd6;
}

.chain-title .dot.ok {
  background: #2f9e63;
  box-shadow: 0 0 0 3px rgba(47, 158, 99, 0.16);
}

.chain-title .dot.bad {
  background: #e05252;
  box-shadow: 0 0 0 3px rgba(224, 82, 82, 0.16);
}

.verify-result {
  margin-top: 12px;
  border-radius: 10px;
  padding: 12px;
  background: #f6f9f7;
}

.verify-result.ok {
  background: #f0f9f4;
}

.verify-result.bad {
  background: #fdf3f3;
}

.v-main {
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 5px;
}

.verify-result.ok .v-main {
  color: #1f7a4c;
}

.verify-result.bad .v-main {
  color: #c94343;
}

.v-sub {
  font-size: 11px;
  color: #6b7c8f;
  margin-top: 6px;
  line-height: 1.6;
}

.v-nums {
  display: flex;
  gap: 14px;
  font-size: 10px;
  color: #8b9aaa;
  margin-top: 8px;
}

.verify-note {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 12px;
  line-height: 1.7;
  background: #f6f9f7;
  border-radius: 10px;
  padding: 11px 12px;
}

.chain-meta {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f5;
}

.meta-row {
  display: flex;
  gap: 10px;
  font-size: 11px;
  line-height: 1.9;
}

.m-label {
  color: #96a3b1;
  flex-shrink: 0;
  width: 56px;
}

.timeline {
  padding: 0 14px;
}

.tl-item {
  display: flex;
  gap: 12px;
}

.tl-axis {
  width: 26px;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.tl-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #eaf7f0;
  color: #2f9e63;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
  z-index: 1;
}

.tl-item.ABNORMAL .tl-dot {
  background: #fdeeee;
  color: #e05252;
}

.tl-item.QUALITY .tl-dot {
  background: #eaf1ff;
  color: #3b82f6;
}

.tl-item.LOGISTICS .tl-dot {
  background: #fff5e8;
  color: #d99a2b;
}

.tl-item.SALES .tl-dot {
  background: #f3edff;
  color: #8b5cf6;
}

.tl-line {
  flex: 1;
  width: 2px;
  background: #eef2f5;
  margin: 4px 0;
}

.tl-body {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 12px 13px;
  margin-bottom: 10px;
  box-shadow: 0 3px 12px rgba(31, 61, 45, 0.035);
}

.tl-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.tl-title {
  font-size: 13px;
  font-weight: 600;
}

.tl-time {
  font-size: 10px;
  color: #96a3b1;
  flex-shrink: 0;
}

.tl-detail {
  font-size: 12px;
  color: #4a5a6a;
  line-height: 1.75;
  margin-top: 7px;
}

.tl-operator {
  font-size: 11px;
  color: #8b9aaa;
  margin-top: 7px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.tl-hash {
  margin-top: 8px;
  background: #f7f9fa;
  border-radius: 7px;
  padding: 7px 9px;
  display: flex;
  gap: 8px;
}

.h-label {
  font-size: 10px;
  color: #96a3b1;
  flex-shrink: 0;
}

.tl-hash .mono {
  color: #5a6b7b;
  font-size: 10px;
  line-height: 1.6;
}

.tl-chain {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.chip {
  font-size: 9px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f3f6f8;
  color: #8b9aaa;
}

.chip.SUCCESS {
  background: #eaf7f0;
  color: #1f7a4c;
}

.chip.FAILED {
  background: #fdeeee;
  color: #c94343;
}

.tx {
  font-size: 9px;
  color: #96a3b1;
}

.foot-note {
  margin: 18px 16px 0;
  font-size: 10px;
  color: #9aa8b6;
  line-height: 1.9;
  text-align: center;
}

.intro {
  margin: 18px 14px 0;
}

.intro-title {
  font-size: 13px;
  font-weight: 600;
  color: #3d4d5c;
  margin-bottom: 12px;
}

.intro-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.intro-item {
  background: #fff;
  border-radius: 13px;
  padding: 14px;
  box-shadow: 0 3px 12px rgba(31, 61, 45, 0.04);
}

.i-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}

.i-title {
  font-size: 12px;
  font-weight: 600;
  margin-top: 9px;
}

.i-desc {
  font-size: 10px;
  color: var(--text-secondary);
  margin-top: 4px;
  line-height: 1.6;
}
</style>
