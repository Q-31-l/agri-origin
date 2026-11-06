<template>
  <pc-shell title="批次详情">
    <view class="pc-page">
      <view v-if="loading" class="pc-none">加载中…</view>
      <view v-else-if="!batch.id" class="pc-none">批次不存在或无权限查看</view>

      <block v-else>
        <!-- 页头 -->
        <view class="pc-head">
          <view class="col">
            <view class="title-row">
              <text class="pc-title mono">{{ batch.batchNo }}</text>
              <pc-status :value="batch.status" type="batch" />
              <pc-status v-if="batch.auditStatus" :value="batch.auditStatus" type="audit" />
              <text v-if="batch.abnormalFlag === 1" class="abn-chip">异常熔断中</text>
            </view>
            <text class="pc-sub">
              {{ batch.productName }}<text v-if="batch.variety"> · {{ batch.variety }}</text>
              · 溯源码 {{ batch.traceCode }}
            </text>
          </view>
          <view class="pc-acts">
            <view class="pc-btn ghost" @click="onVerify">一致性核验</view>
            <view v-if="canAudit" class="pc-btn" @click="auditVisible = true">审核</view>
            <view
              v-if="canClose"
              class="pc-btn ghost danger-ghost"
              @click="onClose"
            >关闭批次</view>
          </view>
        </view>

        <view class="pc-cols">
          <!-- 左侧主区 -->
          <view class="pc-col-main">
            <view v-if="trust" class="pc-card trust-card">
              <view class="pc-card-head">
                <text class="pc-card-title">溯源可信度 · AgriTrust-5D</text>
                <view class="tt-tag" :class="'g-' + trust.grade">
                  <text class="tt-gd">{{ trust.grade }}</text>
                  <text class="tt-gt">{{ trust.gradeText }}</text>
                </view>
              </view>
              <view class="pc-card-body">
                <view class="tt-main">
                  <view class="tt-num">
                    <text class="tn">{{ trust.score }}</text>
                    <text class="tu">分</text>
                  </view>
                  <view class="tt-dims">
                    <view v-for="d in trust.dimensions" :key="d.key" class="td">
                      <view class="td-top">
                        <text class="td-name">{{ d.name }}</text>
                        <text class="td-val">{{ d.score }}<text class="td-max">/{{ d.max }}</text></text>
                      </view>
                      <view class="td-track">
                        <view class="td-fill" :style="{ width: Math.round((d.score / d.max) * 100) + '%' }"></view>
                      </view>
                    </view>
                  </view>
                </view>
                <text class="tt-sum">{{ trust.summary }}</text>
                <text v-if="trust.scope" class="tt-scope">{{ trust.scope }}</text>
                <view v-if="trust.deductions && trust.deductions.length" class="tt-dd">
                  <text v-for="(x, i) in trust.deductions" :key="i" class="tt-dd-item">· {{ x }}</text>
                </view>
              </view>
            </view>

            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">基础信息</text>
              </view>
              <view class="pc-card-body">
                <view class="kv-grid">
                  <view class="pc-kv"><text class="pc-k">产品</text><text class="pc-v">{{ batch.productName || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">品种</text><text class="pc-v">{{ batch.variety || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">农场</text><text class="pc-v">{{ batch.farmName || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">农户</text><text class="pc-v">{{ batch.farmerName || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">所属村</text><text class="pc-v">{{ batch.villageName || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">产量</text><text class="pc-v">{{ formatQty(batch.quantity, batch.unit) }}</text></view>
                  <view class="pc-kv"><text class="pc-k">种植日期</text><text class="pc-v">{{ batch.plantingDate || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">采收日期</text><text class="pc-v">{{ batch.harvestDate || '-' }}</text></view>
                  <view class="pc-kv"><text class="pc-k">审核状态</text><text class="pc-v">{{ auditText }}</text></view>
                  <view class="pc-kv"><text class="pc-k">创建时间</text><text class="pc-v">{{ formatDateTime(batch.createTime) }}</text></view>
                  <view class="pc-kv full"><text class="pc-k">备注</text><text class="pc-v">{{ batch.remark || '-' }}</text></view>
                  <view v-if="batch.auditRemark" class="pc-kv full">
                    <text class="pc-k">审核意见</text><text class="pc-v">{{ batch.auditRemark }}</text>
                  </view>
                </view>
              </view>
            </view>

            <!-- 环节记录 -->
            <view class="pc-card">
              <view class="pc-toolbar">
                <view
                  v-for="t in tabs"
                  :key="t.key"
                  class="tab"
                  :class="{ on: tab === t.key }"
                  @click="tab = t.key"
                >
                  {{ t.label }}<text class="cnt">{{ t.list.length }}</text>
                </view>
              </view>

              <!-- 农事 -->
              <block v-if="tab === 'planting'">
                <view v-if="!plantings.length" class="pc-none">暂无农事记录</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">操作日期</text>
                    <text class="pc-th">类型</text>
                    <text class="pc-th f2">内容</text>
                    <text class="pc-th">投入品</text>
                    <text class="pc-th">用量</text>
                    <text class="pc-th">操作人</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="r in plantings" :key="r.id" class="pc-tr">
                    <text class="pc-td">{{ r.operationDate || '-' }}</text>
                    <text class="pc-td">{{ plantingTypeLabel(r.recordType) }}</text>
                    <view class="pc-td f2">
                      <text class="cell-main">{{ r.content || '-' }}</text>
                      <text v-if="r.location" class="cell-sub">作业地点：{{ r.location }}</text>
                    </view>
                    <text class="pc-td">{{ r.materialName || '-' }}</text>
                    <text class="pc-td">{{ r.dosage || '-' }}</text>
                    <text class="pc-td">{{ r.operatorName || '-' }}</text>
                    <view class="pc-td"><pc-status :value="r.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>

              <!-- 采收 -->
              <block v-else-if="tab === 'harvest'">
                <view v-if="!harvests.length" class="pc-none">暂无采收记录</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">采收日期</text>
                    <text class="pc-th">采收方式</text>
                    <text class="pc-th f2">采收量</text>
                    <text class="pc-th">操作人</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="r in harvests" :key="r.id" class="pc-tr">
                    <text class="pc-td">{{ r.harvestDate || '-' }}</text>
                    <text class="pc-td">{{ r.harvestMethod || '-' }}</text>
                    <view class="pc-td f2">{{ formatQty(r.quantity, r.unit) }}</view>
                    <text class="pc-td">{{ r.operatorName || '-' }}</text>
                    <view class="pc-td"><pc-status :value="r.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>

              <!-- 质检 -->
              <block v-else-if="tab === 'quality'">
                <view v-if="!qualities.length" class="pc-none">暂无质检报告</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">报告编号</text>
                    <text class="pc-th">检测机构</text>
                    <text class="pc-th">检测日期</text>
                    <text class="pc-th">结论</text>
                    <text class="pc-th">标准</text>
                    <text class="pc-th">报告文件</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="r in qualities" :key="r.id" class="pc-tr">
                    <text class="pc-td mono">{{ r.reportNo || '-' }}</text>
                    <text class="pc-td">{{ r.inspectOrg || '-' }}</text>
                    <text class="pc-td">{{ r.inspectDate || '-' }}</text>
                    <view class="pc-td"><pc-status :value="r.inspectResult" type="quality" /></view>
                    <text class="pc-td">{{ r.standardNo || '-' }}</text>
                    <view class="pc-td">
                      <text v-if="r.filePath" class="pc-link" @click="openFile(r)">查看</text>
                      <text v-else class="muted">-</text>
                    </view>
                    <view class="pc-td"><pc-status :value="r.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>

              <!-- 物流 -->
              <block v-else-if="tab === 'logistics'">
                <view v-if="!logistics.length" class="pc-none">暂无物流记录</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">物流单号</text>
                    <text class="pc-th">发货方</text>
                    <text class="pc-th">收货方</text>
                    <text class="pc-th">车牌</text>
                    <text class="pc-th">发运时间</text>
                    <text class="pc-th">状态</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="r in logistics" :key="r.id" class="pc-tr">
                    <text class="pc-td mono">{{ r.logisticsNo || '-' }}</text>
                    <text class="pc-td">{{ r.fromOrg || '-' }}</text>
                    <text class="pc-td">{{ r.toOrg || '-' }}</text>
                    <text class="pc-td">{{ r.vehicleNo || '-' }}</text>
                    <text class="pc-td">{{ formatDateTime(r.departTime) }}</text>
                    <text class="pc-td">{{ r.status || '-' }}</text>
                    <view class="pc-td"><pc-status :value="r.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>

              <!-- 销售 -->
              <block v-else-if="tab === 'sales'">
                <view v-if="!sales.length" class="pc-none">暂无销售记录</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">销售日期</text>
                    <text class="pc-th">市场</text>
                    <text class="pc-th">销售商</text>
                    <text class="pc-th">单价</text>
                    <text class="pc-th">数量</text>
                    <text class="pc-th">买家</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="r in sales" :key="r.id" class="pc-tr">
                    <text class="pc-td">{{ r.salesDate || '-' }}</text>
                    <text class="pc-td">{{ r.marketName || '-' }}</text>
                    <text class="pc-td">{{ r.sellerName || '-' }}</text>
                    <text class="pc-td">￥{{ r.price || '-' }}</text>
                    <text class="pc-td">{{ formatQty(r.quantity, r.unit) }}</text>
                    <text class="pc-td">{{ r.buyerName || '-' }}</text>
                    <view class="pc-td"><pc-status :value="r.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>

              <!-- 异常处置 -->
              <block v-else-if="tab === 'disposal'">
                <view v-if="!disposals.length" class="pc-none">暂无异常处置记录</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">异常类型</text>
                    <text class="pc-th f2">异常描述</text>
                    <text class="pc-th">处置措施</text>
                    <text class="pc-th">处置结果</text>
                    <text class="pc-th">处置人</text>
                    <text class="pc-th">状态</text>
                  </view>
                  <view v-for="r in disposals" :key="r.id" class="pc-tr">
                    <text class="pc-td">{{ r.abnormalType || '-' }}</text>
                    <view class="pc-td f2">{{ r.description || '-' }}</view>
                    <text class="pc-td">{{ r.disposalMeasure || '-' }}</text>
                    <text class="pc-td">{{ r.disposalResult || '-' }}</text>
                    <text class="pc-td">{{ r.handlerName || '-' }}</text>
                    <text class="pc-td">{{ r.status || '-' }}</text>
                  </view>
                </view>
              </block>

              <!-- 更正说明 -->
              <block v-else>
                <view v-if="!corrections.length" class="pc-none">暂无更正说明</view>
                <view v-else class="pc-tbody">
                  <view class="pc-thead inner">
                    <text class="pc-th">更正环节</text>
                    <text class="pc-th">更正原因</text>
                    <text class="pc-th f2">更正说明</text>
                    <text class="pc-th">操作人</text>
                    <text class="pc-th">时间</text>
                    <text class="pc-th">上链</text>
                  </view>
                  <view v-for="c in corrections" :key="c.id" class="pc-tr">
                    <text class="pc-td">{{ bizTypeLabel(c.bizType) }}#{{ c.bizId }}</text>
                    <text class="pc-td">{{ c.reason || '-' }}</text>
                    <view class="pc-td f2">{{ c.content || '-' }}</view>
                    <text class="pc-td">{{ c.operatorName || '-' }}</text>
                    <text class="pc-td">{{ formatDateTime(c.createTime) }}</text>
                    <view class="pc-td"><pc-status :value="c.chainStatus" type="chain" /></view>
                  </view>
                </view>
              </block>
            </view>

            <!-- 时间轴 -->
            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">全流程履历</text>
                <text class="mini">{{ timeline.length }} 个节点</text>
              </view>
              <view class="pc-card-body">
                <pc-timeline :items="timeline" />
              </view>
            </view>
          </view>

          <!-- 右侧 -->
          <view class="pc-col-side">
            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">溯源二维码</text>
              </view>
              <view class="pc-card-body center">
                <pc-qr :code="batch.traceCode" label="消费者扫码查看全流程履历" />
                <view class="qr-acts">
                  <view class="pc-btn ghost sm" @click="copy(batch.traceCode)">复制溯源码</view>
                  <view class="pc-btn ghost sm" @click="openTrace">打开溯源页</view>
                </view>
              </view>
            </view>

            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">链上存证</text>
                <pc-status :value="batch.chainStatus" type="chain" />
              </view>
              <view class="pc-card-body">
                <view class="pc-kv">
                  <text class="pc-k">交易哈希</text>
                  <text class="pc-v mono">{{ batch.chainTxHash || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">区块高度</text>
                  <text class="pc-v">{{ batch.blockNumber || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">存证流水</text>
                  <text class="pc-v">{{ chainRecords.length }} 条</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">链服务</text>
                  <text class="pc-v">{{ chainEnabled ? '已连接 FISCO-BCOS' : '演示模式（未启用）' }}</text>
                </view>
                <text v-if="chainMessage" class="chain-msg">{{ chainMessage }}</text>
              </view>
            </view>

            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">存证流水</text>
              </view>
              <view class="pc-card-body">
                <view v-if="!chainRecords.length" class="pc-none small">暂无存证记录</view>
                <view v-for="c in chainRecords" :key="c.id" class="chain-row">
                  <view class="cr-top">
                    <text class="cr-type">{{ eventName(c.eventType) }}</text>
                    <text class="cr-status" :class="c.status.toLowerCase()">{{ chainText(c.status) }}</text>
                  </view>
                  <text class="cr-hash">tx {{ shortHash(c.txHash) }}</text>
                  <text class="cr-meta">区块 {{ c.blockNumber || '-' }} · {{ formatDateTime(c.createTime) }}</text>
                  <text v-if="c.errorMsg" class="cr-err">{{ c.errorMsg }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </block>

      <!-- 审核弹窗 -->
      <pc-modal v-model="auditVisible" title="批次审核" width="460px" @ok="submitAudit">
        <view class="pc-form">
          <view class="pc-field full">
            <text class="pc-label">审核结论</text>
            <pc-select v-model="auditForm.auditStatus" :options="auditOptions" width="100%" />
          </view>
          <view class="pc-field full">
            <text class="pc-label">审核意见</text>
            <textarea
              class="pc-textarea"
              v-model="auditForm.auditRemark"
              placeholder="驳回时必填"
              maxlength="200"></textarea>
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcQr from '@/components/pc/pc-qr.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcTimeline from '@/components/pc/pc-timeline.vue'
import { batchDetail, verifyBatch, auditBatch, closeBatch } from '@/api'
import { useUserStore } from '@/store/user'
import { AUDIT_STATUS, EVENT_TYPE, plantingTypeLabel } from '@/config/constants'
import { ROUTES } from '@/config/routes'
import { buildUrl } from '@/utils/router'
import { confirm, toast } from '@/utils/ui'
import { formatDateTime, formatQty, shortHash } from '@/utils/format'
import { resolveFileUrl } from '@/utils/file'

const store = useUserStore()

const batchId = ref('')
const loading = ref(true)
const detail = ref({})
const tab = ref('planting')

const batch = computed(() => detail.value.batch || {})
const timeline = computed(() => detail.value.timeline || [])
const plantings = computed(() => detail.value.plantings || [])
const harvests = computed(() => detail.value.harvests || [])
const qualities = computed(() => detail.value.qualities || [])
const logistics = computed(() => detail.value.logistics || [])
const sales = computed(() => detail.value.sales || [])
const disposals = computed(() => detail.value.disposals || [])
const corrections = computed(() => detail.value.corrections || [])
const chainRecords = computed(() => detail.value.chainRecords || [])
const chainEnabled = computed(() => !!detail.value.chainEnabled)
const chainMessage = computed(() => detail.value.chainMessage || '')
const trust = computed(() => detail.value.trust || null)

const auditText = computed(() => {
  const c = AUDIT_STATUS[batch.value.auditStatus]
  return c ? c.text : batch.value.auditStatus || '-'
})

const tabs = computed(() => [
  { key: 'planting', label: '农事记录', list: plantings.value },
  { key: 'harvest', label: '采收', list: harvests.value },
  { key: 'quality', label: '质检报告', list: qualities.value },
  { key: 'logistics', label: '物流', list: logistics.value },
  { key: 'sales', label: '销售', list: sales.value },
  { key: 'disposal', label: '异常处置', list: disposals.value },
  { key: 'correction', label: '更正说明', list: corrections.value }
])

/* ---------------- 权限 ---------------- */
const canAudit = computed(() => {
  if (!store.hasPerm('batch:audit')) return false
  return batch.value.auditStatus === 'PENDING'
})

const canClose = computed(() => {
  if (!store.hasPerm('abnormal:dispose')) return false
  return batch.value.status !== 'CLOSED' && batch.value.abnormalFlag === 1
})

/* ---------------- 数据 ---------------- */
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
  }
}

onLoad((opt) => {
  batchId.value = (opt && opt.id) || ''
  load()
})

/* ---------------- 交互 ---------------- */
function eventName(t) {
  return EVENT_TYPE[t] || t || '-'
}

const BIZ_TYPE_LABEL = {
  PLANTING: '农事',
  HARVEST: '采收',
  QUALITY: '质检',
  LOGISTICS: '物流',
  SALES: '销售',
  ABNORMAL: '异常',
  DISPOSAL: '处置'
}

function bizTypeLabel(t) {
  return BIZ_TYPE_LABEL[t] || t || '-'
}

const CHAIN_TEXT = {
  SUCCESS: '已上链',
  FAILED: '上链失败',
  SKIPPED: '演示跳过',
  NONE: '未存证'
}

function chainText(s) {
  return CHAIN_TEXT[s] || s || '-'
}

async function onVerify() {
  uni.showLoading({ title: '核验中…' })
  try {
    const res = await verifyBatch(batchId.value)
    const d = res.data || {}
    uni.hideLoading()
    uni.showModal({
      title: d.passed ? '核验通过' : '核验未通过',
      content: d.message || (d.passed ? '链上链下数据完全一致' : '存在不一致记录'),
      showCancel: false
    })
  } catch (e) {
    uni.hideLoading()
    toast(e && e.message ? e.message : '核验失败')
  }
}

const auditVisible = ref(false)
const auditOptions = [
  { value: 'APPROVED', label: '审核通过' },
  { value: 'REJECTED', label: '驳回' }
]
const auditForm = reactive({ auditStatus: 'APPROVED', auditRemark: '' })

async function submitAudit() {
  if (auditForm.auditStatus === 'REJECTED' && !auditForm.auditRemark.trim()) {
    return toast('驳回时必须填写审核意见')
  }
  try {
    await auditBatch(batchId.value, {
      auditStatus: auditForm.auditStatus,
      auditRemark: auditForm.auditRemark
    })
    uni.showToast({ title: '审核完成', icon: 'success' })
    auditVisible.value = false
    load()
  } catch (e) {
    /* 已提示 */
  }
}

async function onClose() {
  const ok = await confirm('关闭后该批次将终止流转，确定关闭吗？', '关闭批次')
  if (!ok) return
  try {
    await closeBatch(batchId.value)
    uni.showToast({ title: '批次已关闭', icon: 'success' })
    load()
  } catch (e) {
    /* 已提示 */
  }
}

function copy(text) {
  if (!text) return
  uni.setClipboardData({
    data: String(text),
    success: () => uni.showToast({ title: '已复制', icon: 'none' })
  })
}

function openTrace() {
  uni.navigateTo({
    url: `${ROUTES.PC_TRACE}?code=${encodeURIComponent(batch.value.traceCode || '')}`
  })
}

function openFile(r) {
  const url = resolveFileUrl(r.filePath)
  if (!url) return toast('报告文件不存在')
  // PC 端仅在 H5 运行，直接新标签打开
  if (typeof window !== 'undefined') {
    window.open(url, '_blank')
  } else {
    uni.setClipboardData({ data: url })
  }
}
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.mono {
  font-family: Consolas, monospace;
}
.abn-chip {
  font-size: 10.5px;
  color: #d93026;
  background: #fdeceb;
  border: 1px solid #f7c9c5;
  border-radius: 4px;
  padding: 1px 7px;
}
.mini {
  font-size: 11px;
  color: #94a3b8;
}
.muted {
  color: #c3ccd6;
  font-size: 12px;
}
.danger-ghost {
  color: #d93026 !important;
  border-color: #f0c4c1 !important;
}

/* ---------------- 可信度卡片 ---------------- */
.trust-card .pc-card-body {
  padding: 16px 18px 14px;
}
.tt-tag {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  border-radius: 4px;
  font-size: 11px;
}
.tt-gd {
  font-weight: 700;
}
.g-A {
  background: #e8f7ee;
  color: #1f7a4d;
}
.g-B {
  background: #e7f5f3;
  color: #0f766e;
}
.g-C {
  background: #fff1e6;
  color: #c2410c;
}
.g-D {
  background: #fdeceb;
  color: #d93026;
}
.tt-main {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 24px;
}
.tt-num {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  flex-shrink: 0;
}
.tn {
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
  color: #1f2937;
  letter-spacing: -1px;
}
.tu {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 2px;
}
.tt-dims {
  flex: 1;
  display: flex;
  flex-direction: row;
  gap: 14px;
  min-width: 0;
}
.td {
  flex: 1;
  min-width: 0;
}
.td-top {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 5px;
}
.td-name {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}
.td-val {
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
}
.td-max {
  font-size: 10px;
  font-weight: 400;
  color: #b6bec9;
}
.td-track {
  height: 6px;
  border-radius: 3px;
  background: #f1f5f9;
  overflow: hidden;
}
.td-fill {
  height: 6px;
  border-radius: 3px;
  background: linear-gradient(90deg, #4ade80, #1f7a4d);
  transition: width 0.4s ease;
}
.tt-sum {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-top: 12px;
}
.tt-scope {
  display: block;
  font-size: 11px;
  color: #b6bec9;
  margin-top: 3px;
}
.tt-dd {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f6;
}
.tt-dd-item {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.9;
}

.kv-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
}.kv-grid .pc-kv {
  width: 33.33%;
  box-sizing: border-box;
  padding-right: 14px;
}
.kv-grid .pc-kv.full {
  width: 100%;
}

.tab {
  font-size: 12px;
  color: #64748b;
  padding: 5px 12px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 5px;
}
.tab:hover {
  background: #f4f7fa;
}
.tab.on {
  background: #e8f5ee;
  color: #1f7a4d;
  font-weight: 500;
}
.cnt {
  font-size: 10px;
  color: #94a3b8;
  background: #eef2f6;
  border-radius: 7px;
  padding: 0 5px;
}
.tab.on .cnt {
  background: #ffffff;
  color: #1f7a4d;
}

.pc-thead.inner .pc-th {
  flex: 1;
}
.pc-thead.inner .pc-th.f2,
.pc-tr .pc-td.f2 {
  flex: 2;
}
.cell-main {
  display: block;
  line-height: 1.45;
}
.cell-sub {
  display: block;
  font-size: 10.5px;
  color: #97a3b0;
  margin-top: 2px;
  line-height: 1.4;
}
.center {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.qr-acts {
  display: flex;
  flex-direction: row;
  gap: 8px;
  margin-top: 12px;
}
.chain-msg {
  display: block;
  font-size: 11px;
  color: #b45309;
  background: #fef6e0;
  border-radius: 5px;
  padding: 6px 8px;
  margin-top: 8px;
  line-height: 1.5;
}
.chain-row {
  border-bottom: 1px dashed #eef2f6;
  padding: 8px 0;
  display: flex;
  flex-direction: column;
}
.chain-row:last-child {
  border-bottom: none;
}
.cr-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.cr-type {
  font-size: 12px;
  color: #1f2937;
}
.cr-status {
  font-size: 10px;
  border-radius: 3px;
  padding: 0 5px;
}
.cr-status.success {
  color: #16a34a;
  background: #e8f7ee;
}
.cr-status.failed {
  color: #d93026;
  background: #fdeceb;
}
.cr-status.skipped {
  color: #b45309;
  background: #fef6e0;
}
.cr-hash {
  font-size: 10.5px;
  color: #64748b;
  font-family: Consolas, monospace;
  margin-top: 3px;
}
.cr-meta {
  font-size: 10.5px;
  color: #b6bec9;
  margin-top: 2px;
}
.cr-err {
  font-size: 10.5px;
  color: #d93026;
  margin-top: 3px;
  line-height: 1.5;
}
.pc-none.small {
  padding: 20px;
  font-size: 11px;
}
</style>
