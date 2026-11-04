<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">销售管理</h1>
        <div class="page-desc">仅完成物流交接的合格批次可销售；异常批次会被接口强制拦截并提示熔断原因</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="ShoppingCart" @click="openCreate">销售登记</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="sellerName" label="销售商" min-width="180" show-overflow-tooltip />
        <el-table-column prop="marketName" label="销售市场" min-width="160" />
        <el-table-column prop="salesDate" label="销售日期" width="120" />
        <el-table-column label="单价" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">¥{{ (Number(row.price) * Number(row.quantity)).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="buyerName" label="购买方" width="120" />
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
        </el-table-column>
        <el-table-column prop="createTime" label="登记时间" width="165" />
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

    <el-dialog v-model="createVisible" title="销售登记" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="销售批次" prop="batchId">
          <el-select v-model="form.batchId" filterable placeholder="选择在途/在售批次" style="width: 100%" @change="onBatch">
            <el-option
              v-for="b in batches"
              :key="b.id"
              :label="`${b.batchNo} · ${b.productName} · 余量约 ${b.quantity}${b.unit}`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="销售市场" prop="marketName">
          <el-input v-model="form.marketName" />
        </el-form-item>
        <el-form-item label="销售日期" prop="salesDate">
          <el-date-picker v-model="form.salesDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单价（元）" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="购买方">
          <el-input v-model="form.buyerName" placeholder="选填" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.buyerPhone" placeholder="选填" />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="异常批次不可销售：若尝试提交，后端将返回 409 并拦截本次操作"
      />
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
import { Search, ShoppingCart } from '@element-plus/icons-vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { batchOptions, createSales, salesPage } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('sales:create'))

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
  marketName: '',
  salesDate: '',
  price: 10,
  quantity: 10,
  unit: 'kg',
  buyerName: '',
  buyerPhone: ''
})

const rules = {
  batchId: [{ required: true, message: '请选择批次', trigger: 'change' }],
  marketName: [{ required: true, message: '请填写销售市场', trigger: 'blur' }],
  salesDate: [{ required: true, message: '请选择销售日期', trigger: 'change' }],
  price: [{ required: true, message: '请填写单价', trigger: 'blur' }],
  quantity: [{ required: true, message: '请填写数量', trigger: 'blur' }]
}

const load = async () => {
  loading.value = true
  try {
    const res = await salesPage({ page: query.page, size: query.size, batchNo: query.batchNo || undefined })
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

const onBatch = (id) => {
  const batch = batches.value.find((b) => b.id === id)
  if (batch) form.unit = batch.unit
}

const openCreate = async () => {
  Object.assign(form, {
    batchId: null,
    marketName: store.user?.orgName || '',
    salesDate: new Date().toISOString().slice(0, 10),
    price: 10,
    quantity: 10,
    unit: 'kg',
    buyerName: '',
    buyerPhone: ''
  })
  createVisible.value = true
  try {
    const [a, b] = await Promise.all([batchOptions({ status: 'IN_TRANSIT' }), batchOptions({ status: 'ON_SALE' })])
    batches.value = [...(a.data || []), ...(b.data || [])]
  } catch (e) {
    /* ignore */
  }
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await createSales(form)
      ElMessage.success('销售记录已登记并上链')
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
</style>
