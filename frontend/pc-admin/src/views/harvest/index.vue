<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">采收登记</h1>
        <div class="page-desc">一个批次仅允许一条采收记录，采收完成后批次进入待质检状态</div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="harvestDate" label="采收日期" width="120" />
        <el-table-column label="采收数量" width="130">
          <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="qualityLevel" label="等级" width="80" />
        <el-table-column prop="weather" label="天气" width="120" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="审核" width="90">
          <template #default="{ row }"><StatusTag :status="row.auditStatus" /></template>
        </el-table-column>
        <el-table-column label="存证" width="100">
          <template #default="{ row }"><ChainBadge :status="row.chainStatus" /></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canAudit && row.auditStatus === 'PENDING'" link type="primary" @click="openAudit(row)">
              审核
            </el-button>
            <span v-else class="muted">—</span>
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

    <el-dialog v-model="auditVisible" title="审核采收信息" width="460px">
      <el-form label-width="80px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio-button label="APPROVED">通过</el-radio-button>
            <el-radio-button label="REJECTED">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.auditRemark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { auditHarvest, harvestPage } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canAudit = computed(() => store.hasPerm('harvest:audit'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, batchNo: '' })

const auditVisible = ref(false)
const current = ref({})
const auditForm = reactive({ auditStatus: 'APPROVED', auditRemark: '' })

const load = async () => {
  loading.value = true
  try {
    const res = await harvestPage({ page: query.page, size: query.size, batchNo: query.batchNo || undefined })
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

const openAudit = (row) => {
  current.value = row
  auditForm.auditStatus = 'APPROVED'
  auditForm.auditRemark = ''
  auditVisible.value = true
}

const submitAudit = async () => {
  saving.value = true
  try {
    await auditHarvest(current.value.id, auditForm)
    ElMessage.success('审核完成')
    auditVisible.value = false
    load()
  } finally {
    saving.value = false
  }
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
