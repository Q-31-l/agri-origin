<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">物流运输</h1>
        <div class="page-desc">仅质检合格批次可发运，异常批次在接口层强制拦截，交接单摘要同步上链</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Van" @click="openCreate">物流交接登记</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="logisticsNo" label="运单号" width="150" />
        <el-table-column prop="carrierName" label="承运方" min-width="180" show-overflow-tooltip />
        <el-table-column label="运输路线" min-width="240">
          <template #default="{ row }">
            <span>{{ row.fromOrg }}</span>
            <el-icon class="arrow"><Right /></el-icon>
            <span>{{ row.toOrg }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="vehicleNo" label="车辆" width="110" />
        <el-table-column prop="departTime" label="发运时间" width="165" />
        <el-table-column prop="temperature" label="温控" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
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

    <el-dialog v-model="createVisible" title="物流交接登记" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="运输批次" prop="batchId">
          <el-select v-model="form.batchId" filterable placeholder="选择质检合格批次" style="width: 100%">
            <el-option
              v-for="b in batches"
              :key="b.id"
              :label="`${b.batchNo} · ${b.productName} · ${b.quantity}${b.unit}`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="运单号" prop="logisticsNo">
          <el-input v-model="form.logisticsNo" />
        </el-form-item>
        <el-form-item label="发货方">
          <el-input v-model="form.fromOrg" />
        </el-form-item>
        <el-form-item label="收货方">
          <el-input v-model="form.toOrg" />
        </el-form-item>
        <el-form-item label="车牌号">
          <el-input v-model="form.vehicleNo" />
        </el-form-item>
        <el-form-item label="司机 / 电话">
          <div class="inline">
            <el-input v-model="form.driverName" placeholder="司机姓名" />
            <el-input v-model="form.driverPhone" placeholder="联系电话" />
          </div>
        </el-form-item>
        <el-form-item label="发运时间" prop="departTime">
          <el-date-picker
            v-model="form.departTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="到达时间">
          <el-date-picker
            v-model="form.arriveTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="温控区间">
          <el-input v-model="form.temperature" placeholder="如 2~8℃" />
        </el-form-item>
        <el-form-item label="交接单">
          <el-upload :http-request="doUpload" :show-file-list="false" accept=".jpg,.jpeg,.png,.pdf">
            <el-button :icon="Upload">上传交接单</el-button>
          </el-upload>
          <div v-if="form.handoverFile" class="file-info">
            <el-icon><Document /></el-icon>
            <span>{{ form.handoverFile }}</span>
            <span class="mono hash">SHA-256 {{ short(form.fileHash) }}</span>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交并上链</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Right, Search, Upload, Van } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { batchOptions, createLogistics, logisticsPage, uploadFile } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('logistics:create'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, batchNo: '' })

const createVisible = ref(false)
const formRef = ref()
const batches = ref([])

const form = reactive({
  batchId: null,
  logisticsNo: '',
  fromOrg: '',
  toOrg: '',
  vehicleNo: '',
  driverName: '',
  driverPhone: '',
  departTime: '',
  arriveTime: '',
  temperature: '2~8℃',
  handoverFile: '',
  fileHash: '',
  remark: ''
})

const rules = {
  batchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  logisticsNo: [{ required: true, message: '请填写运单号', trigger: 'blur' }],
  departTime: [{ required: true, message: '请选择发运时间', trigger: 'change' }]
}

const short = (hash) => (hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—')

const load = async () => {
  loading.value = true
  try {
    const res = await logisticsPage({ page: query.page, size: query.size, batchNo: query.batchNo || undefined })
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
  reload()
}

const openCreate = async () => {
  Object.assign(form, {
    batchId: null,
    logisticsNo: 'YD' + Date.now(),
    fromOrg: store.user?.orgName || '',
    toOrg: '',
    vehicleNo: '',
    driverName: '',
    driverPhone: '',
    departTime: new Date().toISOString().slice(0, 19),
    arriveTime: '',
    temperature: '2~8℃',
    handoverFile: '',
    fileHash: '',
    remark: ''
  })
  createVisible.value = true
  try {
    const [q, t] = await Promise.all([batchOptions({ status: 'QUALIFIED' }), batchOptions({ status: 'IN_TRANSIT' })])
    batches.value = [...(q.data || []), ...(t.data || [])]
  } catch (e) {
    /* ignore */
  }
}

const doUpload = async (options) => {
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'LOGISTICS')
  const res = await uploadFile(fd)
  form.handoverFile = res.data.filePath
  form.fileHash = res.data.fileHash
  ElMessage.success('交接单上传成功')
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await createLogistics(form)
      ElMessage.success('物流交接已登记并上链')
      createVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.arrow {
  margin: 0 8px;
  color: #9aa8b6;
  vertical-align: middle;
}

.inline {
  display: flex;
  gap: 10px;
  width: 100%;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: #4a5a6a;
}
</style>
