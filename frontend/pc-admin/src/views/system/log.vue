<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">操作日志</h1>
        <div class="page-desc">记录所有写操作，包含操作人、角色、请求地址、耗时与结果，用于审计追责</div>
      </div>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.username" placeholder="操作人账号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-input v-model="query.module" placeholder="模块名（如 batch）" clearable style="width: 180px" @keyup.enter="reload" />
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="roleCode" label="角色" width="130" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" label="方法" width="150" />
        <el-table-column prop="method" label="请求方式" width="100" />
        <el-table-column prop="requestUri" label="请求地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" />
        <el-table-column prop="createTime" label="时间" width="165" />
        <el-table-column prop="errorMsg" label="异常" min-width="140" show-overflow-tooltip />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { logPage } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, username: '', module: '' })

const load = async () => {
  loading.value = true
  try {
    const res = await logPage({
      page: query.page,
      size: query.size,
      username: query.username || undefined,
      module: query.module || undefined
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
  query.username = ''
  query.module = ''
  reload()
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
