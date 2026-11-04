<template>
  <div class="page-plain">
    <van-nav-bar title="批次详情" left-arrow fixed placeholder @click-left="$router.back()" />

    <van-loading v-if="loading" class="loading" size="22" vertical>加载中…</van-loading>

    <template v-else-if="detail">
      <!-- 批次概览 -->
      <div class="hero">
        <div class="hero-top">
          <div class="no mono">{{ batch.batchNo }}</div>
          <span class="status" :class="batch.status">{{ statusLabel(batch.status) }}</span>
        </div>
        <div class="pname">{{ batch.productName }}<span v-if="batch.variety"> · {{ batch.variety }}</span></div>
        <div class="qty">{{ batch.quantity }}<span class="unit">{{ batch.unit }}</span></div>
        <div class="hero-meta">
          <span>{{ batch.villageName || '' }}{{ batch.farmName || '' }}</span>
          <span class="sep">|</span>
          <span>农户 {{ batch.farmerName || '—' }}</span>
        </div>
        <div class="hero-chain">
          <span class="dot" :class="batch.chainStatus"></span>
          {{ batch.chainStatus === 'SUCCESS' ? '批次信息已上链' : batch.chainStatus === 'FAILED' ? '批次上链失败' : '演示模式未上链' }}
        </div>
      </div>

      <!-- 关键操作 -->
      <div class="actions">
        <div class="act" @click="$router.push(`/planting/new/${batch.id}`)">
          <van-icon name="edit" />
          <span>新增农事</span>
        </div>
        <div class="act" @click="$router.push('/harvest')">
          <van-icon name="flower-o" />
          <span>采收提交</span>
        </div>
        <div class="act" @click="verifyChain">
          <van-icon name="shield-o" />
          <span>一致性校验</span>
        </div>
        <div class="act" @click="showQr = true">
          <van-icon name="qr" />
          <span>溯源码</span>
        </div>
      </div>

      <!-- 一致性校验结果 -->
      <div v-if="verify" class="card verify" :class="verify.passed ? 'ok' : 'bad'">
        <div class="v-head">
          <van-icon :name="verify.passed ? 'passed' : 'warning-o'" />
          <span>{{ verify.passed ? '链上链下校验通过' : '校验未通过' }}</span>
        </div>
        <div class="v-msg">{{ verify.message }}</div>
        <div class="v-meta">共 {{ verify.totalRecords }} 条存证，匹配 {{ verify.matchedRecords }} 条</div>
      </div>

      <!-- 存证流水 -->
      <van-collapse v-model="activeCollapse" class="collapse">
        <van-collapse-item name="chain" :title="`存证流水（${detail.chainRecords.length} 条）`">
          <div v-if="detail.chainRecords.length" class="chain-list">
            <div v-for="c in detail.chainRecords" :key="c.id" class="chain-row">
              <div class="cr-top">
                <span class="cr-type">{{ eventLabel(c.eventType) }}</span>
                <span class="cr-status" :class="c.status">{{ chainLabel(c.status) }}</span>
              </div>
              <div class="cr-hash mono">{{ c.dataHash }}</div>
              <div class="cr-foot">
                <span>{{ c.createTime }}</span>
                <span v-if="c.blockNumber" class="mono">#{{ c.blockNumber }}</span>
              </div>
            </div>
          </div>
          <div v-else class="muted">暂无存证流水</div>
        </van-collapse-item>
      </van-collapse>

      <!-- 全流程履历 -->
      <div class="section-title">全流程履历</div>
      <div class="timeline">
        <div v-for="(t, i) in detail.timeline" :key="i" class="tl-item" :class="t.type">
          <div class="tl-axis">
            <div class="tl-dot"><van-icon :name="iconOf(t.type)" /></div>
            <div class="tl-line" v-if="i < detail.timeline.length - 1"></div>
          </div>
          <div class="tl-body">
            <div class="tl-head">
              <span class="tl-title">{{ t.title }}</span>
              <span class="tl-time">{{ t.time }}</span>
            </div>
            <div class="tl-detail" v-if="t.detail">{{ t.detail }}</div>
            <div class="tl-operator" v-if="t.operator"><van-icon name="user-o" /> {{ t.operator }}</div>
            <div class="tl-badge" v-if="t.chainStatus">
              <span class="chip" :class="t.chainStatus">{{ chainLabel(t.chainStatus) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 质检报告 -->
      <template v-if="detail.qualities.length">
        <div class="section-title">质量检测</div>
        <div class="card q-card" v-for="q in detail.qualities" :key="q.id">
          <div class="q-top">
            <span class="q-result" :class="q.inspectResult">{{ q.inspectResult === 'PASS' ? '合格' : '不合格' }}</span>
            <span class="muted">{{ q.inspectDate }}</span>
          </div>
          <div class="q-row"><span class="k">报告编号</span><span class="v">{{ q.reportNo }}</span></div>
          <div class="q-row"><span class="k">检测机构</span><span class="v">{{ q.inspectOrg }}</span></div>
          <div class="q-row" v-if="q.conclusion"><span class="k">检测结论</span><span class="v">{{ q.conclusion }}</span></div>
          <div class="q-hash mono" v-if="q.fileHash">文件摘要 {{ q.fileHash }}</div>
        </div>
      </template>

      <!-- 更正说明 -->
      <template v-if="detail.corrections.length">
        <div class="section-title">更正说明</div>
        <div class="card corr" v-for="c in detail.corrections" :key="c.id">
          <div class="corr-head">
            <van-icon name="edit" />
            <span>{{ eventLabel(c.bizType) }} 追加说明</span>
            <span class="muted">{{ c.createTime }}</span>
          </div>
          <div class="corr-text">{{ c.content }}</div>
          <div class="corr-foot" v-if="c.reason">更正原因：{{ c.reason }}</div>
          <div class="corr-foot">申请人：{{ c.operatorName || '—' }}</div>
        </div>
      </template>

      <div class="bottom-space"></div>

      <van-popup v-model:show="showQr" position="bottom" round>
        <div class="qr-sheet">
          <div class="qr-title">产品溯源二维码</div>
          <div class="qr-code mono">{{ batch.traceCode }}</div>
          <div class="qr-tip">消费者扫描包装上的二维码，或访问溯源页输入该编号即可查看全流程履历。</div>
          <van-button block round type="primary" @click="copyCode">复制溯源编号</van-button>
        </div>
      </van-popup>
    </template>

    <van-empty v-else description="批次不存在或无权访问" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { batchDetail, traceVerify } from '@/api'

const route = useRoute()
const loading = ref(true)
const detail = ref(null)
const verify = ref(null)
const showQr = ref(false)
const activeCollapse = ref([])

const batch = computed(() => (detail.value ? detail.value.batch : {}))

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

const eventLabel = (e) =>
  ({
    BATCH: '批次登记',
    PLANTING: '农事记录',
    HARVEST: '采收记录',
    QUALITY: '质检报告',
    LOGISTICS: '物流交接',
    SALES: '销售登记',
    ABNORMAL: '异常标记',
    DISPOSAL: '异常处置',
    CORRECTION: '更正说明',
    CLOSE: '批次结案'
  }[e] || e)

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

const verifyChain = async () => {
  try {
    const res = await traceVerify(batch.value.batchNo)
    verify.value = res.data
    showSuccessToast(res.data.passed ? '校验通过' : '校验未通过')
  } catch (e) {
    /* ignore */
  }
}

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(batch.value.traceCode || '')
    showSuccessToast('已复制')
  } catch (e) {
    showToast('复制失败，请长按选择')
  }
}

onMounted(async () => {
  try {
    const res = await batchDetail(route.params.id)
    detail.value = res.data
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.loading {
  padding: 60px 0;
}

.hero {
  background: linear-gradient(160deg, #123626 0%, #1f5c3e 62%, #2f8556 100%);
  color: #eaf6ef;
  padding: 20px 20px 24px;
  border-radius: 0 0 22px 22px;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.no {
  font-size: 12px;
  color: rgba(207, 227, 216, 0.82);
}

.status {
  font-size: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
}

.status.ABNORMAL {
  background: rgba(224, 82, 82, 0.28);
}

.pname {
  font-size: 20px;
  font-weight: 700;
  margin-top: 12px;
}

.qty {
  font-size: 27px;
  font-weight: 700;
  margin-top: 8px;
  line-height: 1;
}

.qty .unit {
  font-size: 13px;
  font-weight: 400;
  color: rgba(207, 227, 216, 0.75);
  margin-left: 5px;
}

.hero-meta {
  font-size: 11px;
  color: rgba(207, 227, 216, 0.75);
  margin-top: 12px;
}

.hero-meta .sep {
  margin: 0 7px;
  opacity: 0.4;
}

.hero-chain {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  margin-top: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
}

.hero-chain .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #b6c2cf;
}

.hero-chain .dot.SUCCESS {
  background: #8fe6b4;
}

.hero-chain .dot.FAILED {
  background: #ff9b9b;
}

.actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  margin: -14px 14px 0;
  border-radius: 16px;
  padding: 15px 6px;
  position: relative;
  box-shadow: 0 8px 22px rgba(20, 60, 44, 0.07);
}

.act {
  text-align: center;
  font-size: 11px;
  color: #4a5a6a;
}

.act .van-icon {
  display: block;
  font-size: 20px;
  color: var(--primary);
  margin-bottom: 6px;
}

.verify {
  border-left: 3px solid #2f9e63;
}

.verify.bad {
  border-left-color: #e05252;
}

.v-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1f7a4c;
}

.verify.bad .v-head {
  color: #c94343;
}

.v-msg {
  font-size: 11px;
  color: #6b7c8f;
  margin-top: 7px;
  line-height: 1.7;
}

.v-meta {
  font-size: 10px;
  color: #96a3b1;
  margin-top: 6px;
}

.collapse {
  margin: 12px 14px 0;
  border-radius: 14px;
  overflow: hidden;
}

.chain-list {
  padding: 4px 0;
}

.chain-row {
  padding: 10px 0;
  border-bottom: 1px dashed #f0f4f6;
}

.chain-row:last-child {
  border-bottom: none;
}

.cr-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cr-type {
  font-size: 12px;
  font-weight: 600;
}

.cr-status {
  font-size: 9px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f3f6f8;
  color: #8b9aaa;
}

.cr-status.SUCCESS {
  background: #eaf7f0;
  color: #1f7a4c;
}

.cr-status.FAILED {
  background: #fdeeee;
  color: #c94343;
}

.cr-hash {
  color: #7b8a99;
  margin-top: 5px;
  font-size: 10px;
}

.cr-foot {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #a8b4c0;
  margin-top: 5px;
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

.tl-badge {
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

.q-card {
  padding: 14px 15px;
}

.q-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.q-result {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 999px;
  background: #eaf7f0;
  color: #1f7a4c;
}

.q-result.FAIL {
  background: #fdeeee;
  color: #c94343;
}

.q-row {
  display: flex;
  gap: 10px;
  font-size: 12px;
  line-height: 1.9;
}

.q-row .k {
  color: #96a3b1;
  width: 62px;
  flex-shrink: 0;
}

.q-row .v {
  color: #3d4d5c;
  flex: 1;
}

.q-hash {
  margin-top: 8px;
  background: #f7f9fa;
  border-radius: 7px;
  padding: 7px 9px;
  color: #7b8a99;
  font-size: 10px;
  line-height: 1.6;
}

.corr {
  padding: 13px 15px;
}

.corr-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}

.corr-head .van-icon {
  color: var(--warning);
}

.corr-head .muted {
  margin-left: auto;
  font-weight: 400;
}

.corr-text {
  font-size: 12px;
  color: #4a5a6a;
  line-height: 1.75;
  margin-top: 8px;
}

.corr-foot {
  font-size: 10px;
  color: #96a3b1;
  margin-top: 8px;
}

.bottom-space {
  height: 24px;
}

.qr-sheet {
  padding: 24px 22px calc(24px + env(safe-area-inset-bottom));
  text-align: center;
}

.qr-title {
  font-size: 15px;
  font-weight: 600;
}

.qr-code {
  margin: 16px 0 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  padding: 14px;
  background: #f6f9f7;
  border-radius: 10px;
  color: #1f7a4c;
}

.qr-tip {
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.8;
  margin-bottom: 18px;
}

:deep(.van-cell-group__title),
:deep(.van-collapse-item__title) {
  font-size: 13px;
}
</style>
