<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">异常管控</h1>
        <div class="page-desc">质检不合格批次自动熔断，监管人员完成处置与结案，并可反查上下游与责任主体</div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.status" placeholder="处置状态" clearable style="width: 150px">
          <el-option label="处置中" value="HANDLING" />
          <el-option label="已完成" value="FINISHED" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column label="异常类型" width="130">
          <template #default="{ row }">{{ typeLabel(row.abnormalType) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="异常描述" min-width="280" show-overflow-tooltip />
        <el-table-column prop="handlerName" label="处置人" width="100" />
        <el-table-column prop="handleTime" label="上报时间" width="165" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTrace(row)">追溯</el-button>
            <el-button v-if="canDispose && row.status === 'HANDLING'" link type="warning" @click="openDispose(row)">
              处置
            </el-button>
            <el-button v-if="canDispose" link type="danger" @click="doClose(row)">结案</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="disposeVisible" title="异常批次处置" width="560px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="批次">{{ current.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="异常描述">{{ current.description }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px" style="margin-top: 16px">
        <el-form-item label="处置措施">
          <el-input v-model="disposeForm.disposalMeasure" type="textarea" :rows="2" placeholder="如：封存剩余产品并抽样复检" />
        </el-form-item>
        <el-form-item label="处置结果">
          <el-input v-model="disposeForm.disposalResult" type="textarea" :rows="3" placeholder="如：全部无害化处理，未流入市场" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disposeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDispose">提交并上链</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="traceVisible" title="上下游追溯与责任定位" size="58%">
      <div v-if="trace.batch" class="trace-body">
        <div class="trace-head">
          <div>
            <div class="th-title">{{ trace.batch.batchNo }}</div>
            <div class="muted">{{ trace.batch.productName }} · {{ trace.batch.villageName }} · {{ trace.batch.farmerName }}</div>
          </div>
          <StatusTag :status="trace.batch.status" />
        </div>

        <div class="section-title">处置记录</div>
        <el-table :data="trace.disposals" size="small">
          <el-table-column prop="abnormalType" label="类型" width="110">
            <template #default="{ row }">{{ typeLabel(row.abnormalType) }}</template>
          </el-table-column>
          <el-table-column prop="disposalMeasure" label="处置措施" min-width="180" show-overflow-tooltip />
          <el-table-column prop="disposalResult" label="处置结果" min-width="180" show-overflow-tooltip />
          <el-table-column prop="handlerName" label="处置人" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
        </el-table>

        <div class="section-title">上游来源（同农户 / 同地块历史批次）</div>
        <el-table :data="trace.upstream" size="small">
          <el-table-column prop="batchNo" label="批次号" width="150" />
          <el-table-column prop="productName" label="产品" width="110" />
          <el-table-column prop="plantingDate" label="种植日期" width="110" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><StatusTag :status="row.status" /></template>
          </el-table-column>
        </el-table>

        <div class="section-title">下游流向</div>
        <el-table :data="trace.downstream" size="small">
          <el-table-column prop="type" label="环节" width="80" />
          <el-table-column prop="time" label="时间" width="150" />
          <el-table-column prop="from" label="来源" min-width="150" show-overflow-tooltip />
          <el-table-column prop="to" label="去向" min-width="150" show-overflow-tooltip />
          <el-table-column prop="detail" label="说明" min-width="200" show-overflow-tooltip />
        </el-table>

        <div class="section-title">责任主体定位</div>
        <div class="resp-list">
          <div v-for="(r, i) in trace.responsibilities" :key="i" class="resp">
            <div class="resp-duty">{{ r.duty }}</div>
            <div class="resp-subject">{{ r.subject }}</div>
            <div class="resp-role">{{ r.role }}</div>
            <div class="resp-detail">{{ r.detail }}</div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { abnormalPage, abnormalTrace, closeBatch, disposeAbnormal } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canDispose = computed(() => store.hasPerm('abnormal:dispose'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, batchNo: '', status: '' })

const disposeVisible = ref(false)
const current = ref({})
const disposeForm = reactive({ disposalMeasure: '', disposalResult: '' })

const traceVisible = ref(false)
const trace = ref({ batch: null, disposals: [], upstream: [], downstream: [], responsibilities: [] })

const typeLabel = (type) =>
  ({
    PESTICIDE: '农药残留超标',
    HEAVY_METAL: '重金属超标',
    MICROBE: '微生物超标',
    OTHER: '其他原因'
  }[type] || type)

const load = async () => {
  loading.value = true
  try {
    const res = await abnormalPage({
      page: query.page,
      size: query.size,
      batchNo: query.batchNo || undefined,
      status: query.status || undefined
    })
    rows.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  query.page = 1
  load()
}

const reset = () => {
  query.batchNo = ''
  query.status = ''
  reload()
}

const openDispose = (row) => {
  current.value = row
  disposeForm.disposalMeasure = ''
  disposeForm.disposalResult = ''
  disposeVisible.value = true
}

const submitDispose = async () => {
  if (!disposeForm.disposalMeasure || !disposeForm.disposalResult) {
    return ElMessage.warning('请填写处置措施与处置结果')
  }
  saving.value = true
  try {
    await disposeAbnormal(current.value.id, disposeForm)
    ElMessage.success('处置结果已记录并上链')
    disposeVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const doClose = async (row) => {
  await ElMessageBox.confirm(
    `确认对批次 ${row.batchNo} 结案？结案后将永久留痕且不可再流转。`,
    '结案确认',
    { type: 'warning' }
  )
  await closeBatch(row.batchId)
  ElMessage.success('批次已结案')
  load()
}

const openTrace = async (row) => {
  const res = await abnormalTrace(row.id)
  trace.value = res.data
  traceVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.trace-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}

.th-title {
  font-size: 20px;
  font-weight: 700;
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  margin: 22px 0 10px;
  padding-left: 10px;
  border-left: 3px solid var(--primary);
}

.resp-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.resp {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 14px;
  background: #fbfdfc;
}

.resp-duty {
  font-size: 12px;
  color: #2f9e63;
  font-weight: 600;
}

.resp-subject {
  font-size: 15px;
  font-weight: 700;
  margin: 6px 0 4px;
}

.resp-role {
  font-size: 12px;
  color: var(--text-secondary);
}

.resp-detail {
  font-size: 12px;
  color: #6b7c8f;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
