<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">产品品类</h1>
        <div class="page-desc">农产品基础档案，批次建档时从品类库选择，保证产品名称规范统一</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增产品</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="产品名称" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-button type="primary" plain @click="reload">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="productCode" label="产品编码" width="140" />
        <el-table-column prop="productName" label="产品名称" width="140" />
        <el-table-column prop="category" label="品类" width="100" />
        <el-table-column prop="variety" label="品种" width="120" />
        <el-table-column prop="unit" label="计量单位" width="100" />
        <el-table-column prop="shelfLifeDays" label="保质期(天)" width="110" />
        <el-table-column prop="originRegion" label="产地" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑产品' : '新增产品'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" />
        </el-form-item>
        <el-form-item label="品类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="蔬菜" value="蔬菜" />
            <el-option label="水果" value="水果" />
            <el-option label="粮油" value="粮油" />
            <el-option label="茶叶" value="茶叶" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-input v-model="form.variety" />
        </el-form-item>
        <el-form-item label="计量单位">
          <el-input v-model="form.unit" />
        </el-form-item>
        <el-form-item label="保质期(天)">
          <el-input-number v-model="form.shelfLifeDays" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="产地">
          <el-input v-model="form.originRegion" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createProduct, productPage, updateProduct } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('product:create'))
const canEdit = computed(() => store.hasPerm('product:update'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  productName: '',
  category: '水果',
  variety: '',
  unit: 'kg',
  shelfLifeDays: 7,
  originRegion: '福建省福州市闽侯县',
  description: ''
})

const rules = {
  productName: [{ required: true, message: '请填写产品名称', trigger: 'blur' }]
}

const load = async () => {
  loading.value = true
  try {
    const res = await productPage({ page: query.page, size: query.size, keyword: query.keyword || undefined })
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

const openCreate = () => {
  Object.assign(form, {
    id: null,
    productName: '',
    category: '水果',
    variety: '',
    unit: 'kg',
    shelfLifeDays: 7,
    originRegion: '福建省福州市闽侯县',
    description: ''
  })
  dialogVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateProduct(form.id, form)
        ElMessage.success('产品已更新')
      } else {
        await createProduct(form)
        ElMessage.success('产品已创建')
      }
      dialogVisible.value = false
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
