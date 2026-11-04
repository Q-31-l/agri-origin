<template>
  <div class="page" v-loading="loading">
    <div class="head">
      <div class="head-left">
        <el-button link :icon="ArrowLeft" @click="$router.back()">返回</el-button>
        <div class="title-row">
          <h1 class="page-title">{{ batch.batchNo }}</h1>
          <StatusTag :status="batch.status" />
          <ChainBadge :status="batch.chainStatus" />
        </div>
        <div class="page-desc">
          {{ batch.productName }} · {{ batch.villageName }} · {{ batch.farmerName }}
          <span class="dot-sep">·</span>建档于 {{ batch.createTime }}
        </div>
      </div>
      <div class="head-right">
        <el-button :icon="Link" @click="doVerify">一致性校验</el-button>
        <el-button v-if="canCorrect" :icon="EditPen" @click="correctionVisible = true">更正说明</el-button>
      </div>
    </div>

    <el-alert
      v-if="batch.abnormalFlag === 1"
      type="error"
      show-icon
      :closable="false"
      title="该批次已被异常熔断"
      description="质检不合格批次禁止物流发运与终端销售，须由监管人员完成处置并结案。"
      class="alert"
    />

    <div class="top-grid">
      <div class="card info-card">
        <div class="card-title">批次档案</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="产品">{{
            batch.productName }}{{ batch.variety ? ' · ' + batch.variety : '' }}</el-descriptions-item>
          <el-descriptions-item label="溯源码">
            <span class="mono">{{ batch.traceCode }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="种植地块">{{ batch.farmName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="种植农户">{{ batch.farmerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所属村">{{ batch.villageName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="产量">{{ batch.quantity }} {{ batch.unit }}</el-descriptions-item>
          <el-descriptions-item label="种植日期">{{ batch.plantingDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="采收日期">{{ batch.harvestDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="村级核验">
            <StatusTag :status="batch.auditStatus" />
            <span class="muted" style="margin-left: 8px">{{ batch.auditBy || '' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="核验意见">{{ batch.auditRemark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="存证交易" :span="2">
            <span class="mono hash">{{ batch.chainTxHash || '未上链' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="card qr-card">
        <div class="card-title">溯源二维码</div>
        <img v-if="qrUrl" :src="qrUrl" class="qr" alt="溯源二维码" />
        <div class="qr-tip">消费者扫码即可查看全流程履历</div>
        <el-button size="small" plain @click="copyTrace">复制溯源链接</el-button>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <div>
          <div class="card-title">全流程时间轴</div>
          <div class="card-sub">按时间顺序展示流转履历、质检摘要与链上凭证</div>
        </div>
        <div class="chain-legend">
          <span><i class="lg ok"></i>已上链</span>
          <span><i class="lg fail"></i>上链失败</span>
          <span><i class="lg skip"></i>未上链</span>
        </div>
      </div>

      <el-timeline v-if="timeline.length" class="timeline">
        <el-timeline-item
          v-for="(item, index) in timeline"
          :key="index"
          :timestamp="item.time"
          placement="top"
          :color="colorOf(item)"
          :hollow="item.chainStatus !== 'SUCCESS'"
        >
          <div class="tl-card">
            <div class="tl-head">
              <div class="tl-title">{{ item.title }}</div>
              <div class="tl-tags">
                <StatusTag v-if="item.status" :status="item.status" />
                <ChainBadge :status="item.chainStatus" />
              </div>
            </div>
            <div class="tl-detail">{{ item.detail }}</div>
            <div class="tl-foot">
              <span class="muted">操作人：{{ item.operator || '—' }}</span>
              <span v-if="item.txHash" class="mono hash" :title="item.txHash">tx: {{ short(item.txHash) }}</span>
              <span v-if="item.blockNumber" class="muted">区块 #{{ item.blockNumber }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无流转记录" />
    </div>

    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="农事记录" name="planting">
          <el-table :data="detail.plantings" size="small">
            <el-table-column prop="operationDate" label="日期" width="110" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ recordTypeLabel(row.recordType) }}</template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
            <el-table-column prop="materialName" label="投入品" width="130" />
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column label="审核" width="90">
              <template #default="{ row }"><StatusTag :status="row.auditStatus" /></template>
            </el-table-column>
            <el-table-column label="存证" width="100">
              <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="采收记录" name="harvest">
          <el-table :data="detail.harvests" size="small">
            <el-table-column prop="harvestDate" label="采收日期" width="120" />
            <el-table-column label="数量" width="120">
              <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column prop="qualityLevel" label="等级" width="80" />
            <el-table-column prop="weather" label="天气" width="120" />
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
            <el-table-column label="存证" width="100">
              <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="质检报告" name="quality">
          <div v-for="r in detail.qualities" :key="r.id" class="quality-item">
            <div class="q-head">
              <div>
                <b>{{ r.reportNo }}</b>
                <StatusTag :status="r.inspectResult" style="margin-left: 8px" />
              </div>
              <ChainBadge :status="r.chainStatus" />
            </div>
            <div class="q-meta">
              <span>{{ r.inspectOrg }}</span>
              <span>检测日期：{{ r.inspectDate }}</span>
              <span>执行标准：{{ r.standardNo || '—' }}</span>
            </div>
            <div class="q-conclusion">{{ r.conclusion }}</div>
            <div class="q-file">
              <span class="muted">报告文件：</span>
              <el-link v-if="r.filePath" type="primary" :href="r.filePath" target="_blank">{{ r.fileName || '查看报告' }}</el-link>
              <span v-else class="muted">未上传</span>
              <span v-if="r.fileHash" class="mono hash" style="margin-left: 10px">SHA-256 {{ short(r.fileHash) }}</span>
            </div>
          </div>
          <el-empty v-if="!detail.qualities.length" description="暂无质检报告" />
        </el-tab-pane>

        <el-tab-pane label="物流交接" name="logistics">
          <el-table :data="detail.logistics" size="small">
            <el-table-column prop="logisticsNo" label="运单号" width="150" />
            <el-table-column prop="carrierName" label="承运方" width="180" show-overflow-tooltip />
            <el-table-column label="路线" min-width="220">
              <template #default="{ row }">{{ row.fromOrg }} → {{ row.toOrg }}</template>
            </el-table-column>
            <el-table-column prop="vehicleNo" label="车辆" width="110" />
            <el-table-column prop="departTime" label="发运时间" width="165" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><StatusTag :status="row.status" /></template>
            </el-table-column>
            <el-table-column label="存证" width="100">
              <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="销售记录" name="sales">
          <el-table :data="detail.sales" size="small">
            <el-table-column prop="sellerName" label="销售商" width="180" show-overflow-tooltip />
            <el-table-column prop="marketName" label="销售市场" min-width="160" />
            <el-table-column prop="salesDate" label="销售日期" width="120" />
            <el-table-column label="价格" width="100">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
            <el-table-column label="数量" width="110">
              <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
            </el-table-column>
            <el-table-column prop="buyerName" label="购买方" width="120" />
            <el-table-column label="存证" width="100">
              <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="存证流水" name="chain">
          <el-table :data="detail.chainRecords" size="small">
            <el-table-column prop="bizType" label="业务类型" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }"><ChainBadge :status="row.status" /></template>
            </el-table-column>
            <el-table-column label="交易哈希" min-width="220">
              <template #default="{ row }"><span class="mono hash">{{ row.txHash || '—' }}</span></template>
            </el-table-column>
            <el-table-column prop="blockNumber" label="区块" width="100" />
            <el-table-column prop="createTime" label="时间" width="165" />
            <el-table-column prop="errorMsg" label="失败原因" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="更正记录" name="correction">
          <el-table :data="detail.corrections" size="small">
            <el-table-column prop="bizType" label="业务类型" width="120" />
            <el-table-column prop="bizId" label="记录ID" width="90" />
            <el-table-column prop="reason" label="更正原因" min-width="160" />
            <el-table-column prop="content" label="更正说明" min-width="220" show-overflow-tooltip />
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column prop="createTime" label="时间" width="165" />
          </el-table>
          <el-empty v-if="!detail.corrections.length" description="暂无更正记录" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="correctionVisible" title="提交更正说明" width="520px">
      <el-form :model="correctionForm" label-width="96px">
        <el-form-item label="业务类型">
          <el-select v-model="correctionForm.bizType" style="width: 100%">
            <el-option label="农事记录" value="PLANTING" />
            <el-option label="采收记录" value="HARVEST" />
            <el-option label="质检报告" value="QUALITY" />
            <el-option label="物流记录" value="LOGISTICS" />
            <el-option label="销售记录" value="SALES" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录 ID">
          <el-input-number v-model="correctionForm.bizId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="更正原因">
          <el-input v-model="correctionForm.reason" />
        </el-form-item>
        <el-form-item label="更正说明">
          <el-input v-model="correctionForm.content" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctionVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCorrection">提交并上链</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyVisible" title="链上链下一致性校验" width="760px">
      <div class="verify-head" :class="verify.passed ? 'pass' : 'fail'">
        <el-icon><component :is="verify.passed ? 'CircleCheck' : 'CircleClose'" /></el-icon>
        <div>
          <div class="v-title">{{ verify.passed ? '校验通过' : '校验未通过' }}</div>
          <div class="v-sub">{{ verify.message }}</div>
        </div>
      </div>
      <el-table :data="verify.items" size="small">
        <el-table-column prop="name" label="存证记录" min-width="150" />
        <el-table-column label="链下摘要" min-width="150">
          <template #default="{ row }"><span class="mono">{{ short(row.localHash) }}</span></template>
        </el-table-column>
        <el-table-column label="链上摘要" min-width="150">
          <template #default="{ row }"><span class="mono">{{ short(row.chainHash) }}</span></template>
        </el-table-column>
        <el-table-column label="摘要比对" width="100">
          <template #default="{ row }">
            <el-tag :type="row.hashMatched ? 'success' : 'danger'" size="small">
              {{ row.hashMatched ? '一致' : '不一致' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回执" width="90">
          <template #default="{ row }">
            <el-tag :type="row.receiptOk ? 'success' : 'danger'" size="small">{{ row.receiptOk ? '正常' : '异常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结论" width="90">
          <template #default="{ row }">
            <el-tag :type="row.matched ? 'success' : 'danger'" size="small">{{ row.matched ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!verify.chainEnabled" class="muted" style="margin-top: 12px">
        提示：系统未启用链上存证（演示模式），无法完成链上比对。
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { ArrowLeft, EditPen, Link } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { batchDetail, createCorrection, verifyBatch } from '@/api'
import { useUserStore } from '@/store/user'

const route = useRoute()
const store = useUserStore()

const loading = ref(false)
const detail = ref({ plantings: [], harvests: [], qualities: [], logistics: [], sales: [], chainRecords: [], corrections: [], timeline: [] })
const batch = computed(() => detail.value.batch || {})
const timeline = computed(() => detail.value.timeline || [])
const tab = ref('planting')
const qrUrl = ref('')
const correctionVisible = ref(false)
const verifyVisible = ref(false)
const verify = ref({ passed: false, items: [], message: '' })

const canCorrect = computed(
  () => store.roleCode === 'PRODUCER' || store.roleCode === 'VILLAGE_ADMIN' || store.roleCode === 'REGULATOR'
)

const correctionForm = reactive({ batchId: null, bizType: 'PLANTING', bizId: null, reason: '', content: '' })

const h5Base = import.meta.env.VITE_H5_BASE || 'http://localhost:5174'
const traceLink = computed(() => `${h5Base}/#/trace?code=${batch.value.traceCode || ''}`)

const recordTypeLabel = (type) =>
  ({
    SOIL: '整地施肥',
    SOWING: '播种定植',
    FERTILIZE: '追肥',
    IRRIGATE: '灌溉',
    PEST: '病虫害防治',
    WEED: '除草',
    OTHER: '其他农事'
  }[type] || type)

const short = (hash) => (hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—')

const colorOf = (item) => {
  if (item.chainStatus === 'SUCCESS') return '#2f9e63'
  if (item.chainStatus === 'FAILED') return '#e05252'
  return '#c3ccd6'
}

const load = async () => {
  loading.value = true
  try {
    const res = await batchDetail(route.params.id)
    detail.value = { ...detail.value, ...res.data }
    if (batch.value.traceCode) {
      qrUrl.value = await QRCode.toDataURL(traceLink.value, {
        width: 220,
        margin: 1,
        color: { dark: '#173f2c', light: '#ffffff' }
      })
    }
    correctionForm.batchId = batch.value.id
  } finally {
    loading.value = false
  }
}

const doVerify = async () => {
  const res = await verifyBatch(route.params.id)
  verify.value = res.data
  verifyVisible.value = true
}

const copyTrace = async () => {
  try {
    await navigator.clipboard.writeText(traceLink.value)
    ElMessage.success('溯源链接已复制')
  } catch (e) {
    ElMessage.warning('浏览器不支持剪贴板，请手动复制：' + traceLink.value)
  }
}

const submitCorrection = async () => {
  if (!correctionForm.bizId || !correctionForm.reason || !correctionForm.content) {
    return ElMessage.warning('请填写完整的更正信息')
  }
  await createCorrection({ ...correctionForm, batchId: batch.value.id })
  ElMessage.success('更正说明已提交并上链')
  correctionVisible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.head-left .el-button {
  padding-left: 0;
  margin-bottom: 6px;
  color: var(--text-secondary);
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-row .page-title {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 24px;
}

.dot-sep {
  margin: 0 6px;
  color: #c3ccd6;
}

.head-right {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.alert {
  margin-bottom: 16px;
}

.top-grid {
  display: grid;
  grid-template-columns: 1fr 260px;
  gap: 16px;
  margin-bottom: 16px;
}

.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.qr-card .card-title {
  align-self: flex-start;
}

.qr {
  width: 200px;
  height: 200px;
  border-radius: 10px;
  border: 1px solid var(--border);
}

.qr-tip {
  font-size: 12px;
  color: var(--text-secondary);
  text-align: center;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}

.card-sub {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.chain-legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-secondary);
}

.chain-legend .lg {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
}

.lg.ok {
  background: #2f9e63;
}

.lg.fail {
  background: #e05252;
}

.lg.skip {
  background: #c3ccd6;
}

.timeline {
  padding-left: 4px;
  margin-top: 8px;
}

.tl-card {
  background: #fbfdfc;
  border: 1px solid #edf2ef;
  border-radius: 10px;
  padding: 14px 16px;
}

.tl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.tl-title {
  font-weight: 600;
  font-size: 14px;
}

.tl-tags {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.tl-detail {
  font-size: 13px;
  color: #4a5a6a;
  margin-top: 8px;
  line-height: 1.7;
}

.tl-foot {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
  flex-wrap: wrap;
}

.quality-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 12px;
}

.q-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.q-meta {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: var(--text-secondary);
  margin: 10px 0;
  flex-wrap: wrap;
}

.q-conclusion {
  font-size: 13px;
  line-height: 1.7;
  color: #34495e;
}

.q-file {
  margin-top: 10px;
  font-size: 12px;
  display: flex;
  align-items: center;
}

.verify-head {
  display: flex;
  gap: 14px;
  align-items: center;
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 16px;
}

.verify-head.pass {
  background: #eef9f3;
  color: #1f7a4c;
}

.verify-head.fail {
  background: #fdeeee;
  color: #b93b3b;
}

.verify-head .el-icon {
  font-size: 30px;
}

.v-title {
  font-weight: 700;
  font-size: 16px;
}

.v-sub {
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.85;
}

@media (max-width: 1100px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
}
</style>
