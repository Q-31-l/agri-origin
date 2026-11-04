<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">质量检测</h1>
        <div class="page-desc">检测机构上传质检报告并对批次作出判定，不合格批次将自动熔断并上链标记</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Upload" @click="openCreate">上传质检报告</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.result" placeholder="检测结论" clearable style="width: 150px">
          <el-option label="合格" value="PASS" />
          <el-option label="不合格" value="FAIL" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="reportNo" label="报告编号" width="160" />
        <el-table-column prop="inspectOrg" label="检测机构" min-width="200" show-overflow-tooltip />
        <el-table-column prop="inspectDate" label="检测日期" width="120" />
        <el-table-column label="结论" width="100">
          <template #default="{ row }"><StatusTag :status="row.inspectResult" /></template>
        </el-table-column>
        <el-table-column prop="conclusion" label="检测结论说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="报告" width="100">
          <template #default="{ row }">
            <el-link v-if="row.filePath" type="primary" :href="row.filePath" target="_blank">查看</el-link>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="165" />
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

    <el-dialog v-model="createVisible" title="上传质检报告" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="检测批次" prop="batchId">
          <el-select v-model="form.batchId" filterable placeholder="选择已采收批次" style="width: 100%">
            <el-option
              v-for="b in batches"
              :key="b.id"
              :label="`${b.batchNo} · ${b.productName} · ${b.farmerName}`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报告编号" prop="reportNo">
          <el-input v-model="form.reportNo" placeholder="如 JC20260512-001" />
        </el-form-item>
        <el-form-item label="检测日期" prop="inspectDate">
          <el-date-picker v-model="form.inspectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="执行标准">
          <el-input v-model="form.standardNo" placeholder="如 GB 2763-2021" />
        </el-form-item>
        <el-form-item label="检测项目">
          <div class="items">
            <div v-for="(item, index) in items" :key="index" class="item-row">
              <el-input v-model="item.item" placeholder="项目" style="width: 150px" />
              <el-input v-model="item.value" placeholder="检测值" style="width: 110px" />
              <el-input v-model="item.limit" placeholder="限量" style="width: 110px" />
              <el-input v-model="item.unit" placeholder="单位" style="width: 100px" />
              <el-select v-model="item.result" style="width: 100px">
                <el-option label="合格" value="合格" />
                <el-option label="不合格" value="不合格" />
              </el-select>
              <el-button link type="danger" :icon="Delete" @click="items.splice(index, 1)" />
            </div>
            <el-button link type="primary" :icon="Plus" @click="addItem">添加检测项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="检测结论" prop="inspectResult">
          <el-radio-group v-model="form.inspectResult">
            <el-radio-button label="PASS">合格</el-radio-button>
            <el-radio-button label="FAIL">不合格</el-radio-button>
          </el-radio-group>
          <span v-if="form.inspectResult === 'FAIL'" class="fail-tip">不合格将立即熔断该批次并禁止销售</span>
        </el-form-item>
        <el-form-item label="结论说明">
          <el-input v-model="form.conclusion" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="报告文件" prop="fileHash">
          <el-upload
            :http-request="doUpload"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
          >
            <el-button :icon="Upload">选择文件</el-button>
          </el-upload>
          <div v-if="form.fileName" class="file-info">
            <el-icon><Document /></el-icon>
            <span>{{ form.fileName }}</span>
            <span class="mono hash">SHA-256 {{ short(form.fileHash) }}</span>
          </div>
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
import { Delete, Document, Plus, Search, Upload } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { batchOptions, createQuality, qualityPage, uploadFile } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('quality:create'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, batchNo: '', result: '' })

const createVisible = ref(false)
const formRef = ref()
const batches = ref([])
const items = ref([])

const form = reactive({
  batchId: null,
  reportNo: '',
  inspectDate: '',
  standardNo: 'GB 2763-2021',
  inspectResult: 'PASS',
  conclusion: '',
  itemsJson: '',
  fileName: '',
  filePath: '',
  fileHash: '',
  fileSize: 0
})

const rules = {
  batchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  reportNo: [{ required: true, message: '请填写报告编号', trigger: 'blur' }],
  inspectDate: [{ required: true, message: '请选择检测日期', trigger: 'change' }],
  inspectResult: [{ required: true, message: '请选择检测结论', trigger: 'change' }],
  fileHash: [{ required: true, message: '请上传质检报告文件', trigger: 'change' }]
}

const short = (hash) => (hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—')

const load = async () => {
  loading.value = true
  try {
    const res = await qualityPage({
      page: query.page,
      size: query.size,
      batchNo: query.batchNo || undefined,
      result: query.result || undefined
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
  query.result = ''
  reload()
}

const addItem = () => items.value.push({ item: '', value: '', limit: '', unit: 'mg/kg', result: '合格' })

const openCreate = async () => {
  Object.assign(form, {
    batchId: null,
    reportNo: 'JC' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(Date.now()).slice(-4),
    inspectDate: new Date().toISOString().slice(0, 10),
    standardNo: 'GB 2763-2021',
    inspectResult: 'PASS',
    conclusion: '',
    fileName: '',
    filePath: '',
    fileHash: '',
    fileSize: 0
  })
  items.value = [
    { item: '毒死蜱', value: '', limit: '0.02', unit: 'mg/kg', result: '合格' },
    { item: '铅', value: '', limit: '0.1', unit: 'mg/kg', result: '合格' }
  ]
  createVisible.value = true
  try {
    const res = await batchOptions()
    batches.value = (res.data || []).filter((b) => ['HARVESTED', 'QUALIFIED'].includes(b.status))
  } catch (e) {
    /* ignore */
  }
}

const doUpload = async (options) => {
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('bizType', 'QUALITY_REPORT')
  const res = await uploadFile(fd)
  form.fileName = res.data.fileName
  form.filePath = res.data.filePath
  form.fileHash = res.data.fileHash
  form.fileSize = res.data.fileSize
  ElMessage.success('文件上传成功，摘要已生成')
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, itemsJson: JSON.stringify(items.value) }
      await createQuality(payload)
      ElMessage.success(form.inspectResult === 'FAIL' ? '报告已提交，批次已熔断并上链' : '质检报告已提交并上链')
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

.items {
  width: 100%;
}

.item-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.fail-tip {
  color: #e05252;
  font-size: 12px;
  margin-left: 12px;
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
