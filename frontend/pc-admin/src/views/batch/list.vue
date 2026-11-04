<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">批次管理</h1>
        <div class="page-desc">以批次为主线管理农产品全生命周期，核心字段不可篡改，错误通过更正说明追加留痕</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建批次</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="批次号 / 产品 / 农户" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.status" placeholder="批次状态" clearable>
          <el-option v-for="item in statusOptions" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
        <el-select v-model="query.abnormalFlag" placeholder="异常标记" clearable>
          <el-option label="仅异常批次" :value="1" />
          <el-option label="正常批次" :value="0" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <span class="muted">共 {{ total }} 个批次</span>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次编码" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goDetail(row)">{{ row.batchNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品" min-width="110" />
        <el-table-column label="产地 / 农户" min-width="170">
          <template #default="{ row }">
            <div>{{ row.villageName || '—' }}</div>
            <div class="muted">{{ row.farmerName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="产量" width="110">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="村级核验" width="100">
          <template #default="{ row }"><StatusTag :status="row.auditStatus" /></template>
        </el-table-column>
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
        </el-table-column>
        <el-table-column prop="createTime" label="建档时间" width="165" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="canAudit && row.auditStatus === 'PENDING'" link type="warning" @click="openAudit(row)">
              核验
            </el-button>
            <el-button v-if="canCorrect" link @click="openCorrection(row)">更正</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无批次数据，先创建第一个批次" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </div>

    <!-- 新建批次 -->
    <el-dialog v-model="createVisible" title="新建农产品批次" width="560px" destroy-on-close>
      <el-form ref="createRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="产品品类" prop="productId">
          <el-select v-model="form.productId" placeholder="请选择产品" style="width: 100%">
            <el-option v-for="p in products" :key="p.id" :label="`${p.productName}（${p.variety || p.category}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="种植地块" prop="farmId">
          <el-select v-model="form.farmId" placeholder="请选择地块" style="width: 100%" @change="onFarmChange">
            <el-option v-for="f in farms" :key="f.id" :label="`${f.farmName} · ${f.farmerName}`" :value="f.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="种植农户">
          <el-input :model-value="selectedFarmerName" disabled placeholder="选择地块后自动带出" />
        </el-form-item>
        <el-form-item label="种植日期" prop="plantingDate">
          <el-date-picker v-model="form.plantingDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预计产量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计量单位">
          <el-select v-model="form.unit" style="width: 100%">
            <el-option label="kg" value="kg" />
            <el-option label="吨" value="吨" />
            <el-option label="箱" value="箱" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="创建后系统将生成唯一批次编码与溯源码，并把批次摘要上链存证"
        style="margin-bottom: 6px"
      />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建并上链</el-button>
      </template>
    </el-dialog>

    <!-- 村级核验 -->
    <el-dialog v-model="auditVisible" title="批次核验" width="460px">
      <div class="audit-info">
        <div><span class="muted">批次</span>{{ current.batchNo }}</div>
        <div><span class="muted">产品</span>{{ current.productName }}</div>
        <div><span class="muted">农户</span>{{ current.farmerName }}</div>
      </div>
      <el-form label-width="80px">
        <el-form-item label="核验结果">
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio-button label="APPROVED">通过</el-radio-button>
            <el-radio-button label="REJECTED">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="核验意见">
          <el-input v-model="auditForm.auditRemark" type="textarea" :rows="3" placeholder="请填写核验意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 更正说明 -->
    <el-dialog v-model="correctionVisible" title="提交更正说明" width="520px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="系统不覆盖原始记录：更正内容将作为新增记录追加，并同步上链留痕"
        style="margin-bottom: 16px"
      />
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
          <el-input v-model="correctionForm.reason" placeholder="如：采收数量录入笔误" />
        </el-form-item>
        <el-form-item label="更正说明">
          <el-input v-model="correctionForm.content" type="textarea" :rows="3" placeholder="请说明正确内容与依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCorrection">提交并上链</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import {
  auditBatch,
  batchPage,
  createBatch,
  createCorrection,
  dict,
  farmList,
  productList
} from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const farms = ref([])
const statusOptions = ref([])

const query = reactive({ page: 1, size: 10, keyword: '', status: '', abnormalFlag: null })

const canCreate = computed(() => store.hasPerm('batch:create'))
const canAudit = computed(() => store.hasPerm('batch:audit'))
const canCorrect = computed(
  () => store.roleCode === 'PRODUCER' || store.roleCode === 'VILLAGE_ADMIN' || store.roleCode === 'REGULATOR'
)

const load = async () => {
  loading.value = true
  try {
    const res = await batchPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      abnormalFlag: query.abnormalFlag === null || query.abnormalFlag === '' ? undefined : query.abnormalFlag
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
  Object.assign(query, { page: 1, size: 10, keyword: '', status: '', abnormalFlag: null })
  load()
}

const goDetail = (row) => router.push(`/batch/${row.id}`)

/* ---------- 新建 ---------- */
const createVisible = ref(false)
const createRef = ref()
const form = reactive({
  productId: null,
  farmId: null,
  plantingDate: '',
  quantity: 100,
  unit: 'kg',
  remark: ''
})
const rules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  farmId: [{ required: true, message: '请选择地块', trigger: 'change' }],
  plantingDate: [{ required: true, message: '请选择种植日期', trigger: 'change' }],
  quantity: [{ required: true, message: '请填写预计产量', trigger: 'blur' }]
}

const selectedFarmerName = computed(() => {
  const farm = farms.value.find((f) => f.id === form.farmId)
  return farm ? `${farm.farmerName}（${farm.farmName}）` : ''
})

const onFarmChange = (id) => {
  const farm = farms.value.find((f) => f.id === id)
  if (farm) form.unit = 'kg'
}

const openCreate = async () => {
  Object.assign(form, { productId: null, farmId: null, plantingDate: '', quantity: 100, unit: 'kg', remark: '' })
  createVisible.value = true
  try {
    const [p, f] = await Promise.all([productList(), farmList()])
    products.value = p.data || []
    farms.value = f.data || []
  } catch (e) {
    /* ignore */
  }
}

const submitCreate = async () => {
  await createRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const farm = farms.value.find((f) => f.id === form.farmId)
      await createBatch({ ...form, farmerId: farm ? farm.farmerId : null })
      ElMessage.success('批次创建成功，已完成存证登记')
      createVisible.value = false
      reload()
    } finally {
      saving.value = false
    }
  })
}

/* ---------- 核验 ---------- */
const auditVisible = ref(false)
const current = ref({})
const auditForm = reactive({ auditStatus: 'APPROVED', auditRemark: '' })

const openAudit = (row) => {
  current.value = row
  auditForm.auditStatus = 'APPROVED'
  auditForm.auditRemark = ''
  auditVisible.value = true
}

const submitAudit = async () => {
  saving.value = true
  try {
    await auditBatch(current.value.id, auditForm)
    ElMessage.success('核验完成')
    auditVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* ---------- 更正 ---------- */
const correctionVisible = ref(false)
const correctionForm = reactive({ bizType: 'PLANTING', bizId: null, reason: '', content: '' })

const openCorrection = (row) => {
  Object.assign(correctionForm, {
    bizType: 'PLANTING',
    bizId: null,
    reason: '',
    content: '',
    batchId: row.id
  })
  correctionVisible.value = true
}

const submitCorrection = async () => {
  if (!correctionForm.bizId || !correctionForm.reason || !correctionForm.content) {
    return ElMessage.warning('请填写完整的更正信息')
  }
  saving.value = true
  try {
    await createCorrection(correctionForm)
    ElMessage.success('更正说明已提交并上链')
    correctionVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const d = await dict('batch_status')
    statusOptions.value = (d.data || []).map((i) => ({ code: i.dictCode, label: i.dictLabel }))
  } catch (e) {
    statusOptions.value = [
      { code: 'PLANTING', label: '种植中' },
      { code: 'HARVESTED', label: '已采收' },
      { code: 'QUALIFIED', label: '质检合格' },
      { code: 'IN_TRANSIT', label: '运输中' },
      { code: 'ON_SALE', label: '在售' },
      { code: 'SOLD', label: '已售出' },
      { code: 'ABNORMAL', label: '异常管控' },
      { code: 'CLOSED', label: '已结案' }
    ]
  }
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.audit-info {
  background: #f7faf8;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  gap: 24px;
  font-size: 13px;
  margin-bottom: 16px;
}

.audit-info .muted {
  margin-right: 6px;
}
</style>
