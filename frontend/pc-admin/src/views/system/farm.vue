<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">农场档案</h1>
        <div class="page-desc">地块与农户绑定关系，是批次建档与网格化管控的基础数据</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增农场</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="农场名称 / 农户 / 编码" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.villageId" placeholder="所属村" clearable style="width: 160px">
          <el-option v-for="v in villages" :key="v.id" :label="v.villageName" :value="v.id" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="farmCode" label="农场编码" width="170" />
        <el-table-column prop="farmName" label="农场名称" min-width="170" />
        <el-table-column prop="farmerName" label="承包农户" width="110" />
        <el-table-column prop="villageName" label="所属村" width="110" />
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="面积" width="100">
          <template #default="{ row }">{{ row.area ? row.area + ' 亩' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="soilType" label="土壤类型" width="100" />
        <el-table-column prop="certNo" label="确权编号" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑农场' : '新增农场'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="农场名称" prop="farmName">
          <el-input v-model="form.farmName" />
        </el-form-item>
        <el-form-item label="所属村" prop="villageId">
          <el-select v-model="form.villageId" style="width: 100%" :disabled="store.roleCode === 'VILLAGE_ADMIN'">
            <el-option v-for="v in villages" :key="v.id" :label="v.villageName" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="承包农户" prop="farmerId">
          <el-select v-model="form.farmerId" filterable style="width: 100%">
            <el-option v-for="f in farmers" :key="f.id" :label="`${f.realName}（${f.username}）`" :value="f.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="面积（亩）">
          <el-input-number v-model="form.area" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="土壤类型">
          <el-input v-model="form.soilType" />
        </el-form-item>
        <el-form-item label="灌溉水源">
          <el-input v-model="form.waterSource" />
        </el-form-item>
        <el-form-item label="确权编号">
          <el-input v-model="form.certNo" />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createFarm, farmPage, updateFarm, userList, villageList } from '@/api'
import { useUserStore } from '@/store/user'
import { computed } from 'vue'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('farm:create'))
const canEdit = computed(() => store.hasPerm('farm:update'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const villages = ref([])
const farmers = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', villageId: null })

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  farmName: '',
  villageId: null,
  farmerId: null,
  address: '',
  area: null,
  soilType: '',
  waterSource: '',
  certNo: '',
  status: 1
})

const rules = {
  farmName: [{ required: true, message: '请填写农场名称', trigger: 'blur' }],
  villageId: [{ required: true, message: '请选择所属村', trigger: 'change' }],
  farmerId: [{ required: true, message: '请选择承包农户', trigger: 'change' }]
}

const load = async () => {
  loading.value = true
  try {
    const res = await farmPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      villageId: query.villageId || undefined
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
  query.keyword = ''
  query.villageId = null
  reload()
}

const openCreate = async () => {
  Object.assign(form, {
    id: null,
    farmName: '',
    villageId: store.roleCode === 'VILLAGE_ADMIN' ? store.user?.villageId : null,
    farmerId: null,
    address: '',
    area: null,
    soilType: '',
    waterSource: '',
    certNo: '',
    status: 1
  })
  dialogVisible.value = true
  await loadFarmers()
}

const openEdit = async (row) => {
  Object.assign(form, { ...row })
  dialogVisible.value = true
  await loadFarmers()
}

const loadFarmers = async () => {
  try {
    const res = await userList({ roleCode: 'FARMER' })
    farmers.value = res.data || []
  } catch (e) {
    /* ignore */
  }
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateFarm(form.id, form)
        ElMessage.success('农场已更新')
      } else {
        await createFarm(form)
        ElMessage.success('农场已创建')
      }
      dialogVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

onMounted(async () => {
  load()
  try {
    const res = await villageList()
    villages.value = res.data || []
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
