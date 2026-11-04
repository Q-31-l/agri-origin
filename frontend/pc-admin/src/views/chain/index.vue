<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">链上存证</h1>
        <div class="page-desc">全部上链流水与回执状态；失败记录同样留痕，前端不会显示虚假的存证成功</div>
      </div>
      <el-button :icon="CircleCheck" type="primary" plain @click="verifyVisible = true">一致性校验</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.status" placeholder="存证状态" clearable style="width: 150px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="未上链" value="SKIPPED" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <div class="spacer"></div>
        <div class="chain-state">
          <span class="chain-dot" :class="chain.enabled && chain.ready ? 'ok' : 'skip'"></span>
          {{ chain.enabled ? (chain.ready ? '链服务已连接' : '链服务异常') : '演示模式' }}
          <span v-if="chain.blockNumber" class="muted">· 区块 {{ chain.blockNumber }}</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><ChainBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="数据摘要" min-width="200">
          <template #default="{ row }"><span class="mono hash">{{ short(row.dataHash) }}</span></template>
        </el-table-column>
        <el-table-column label="交易哈希" min-width="200">
          <template #default="{ row }"><span class="mono">{{ row.txHash || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="blockNumber" label="区块" width="100" />
        <el-table-column prop="createTime" label="时间" width="165" />
        <el-table-column prop="errorMsg" label="失败原因" min-width="160" show-overflow-tooltip />
        <template #empty>
          <div class="chain-empty">
            <el-icon class="chain-empty-icon"><Link /></el-icon>
            <div class="chain-empty-title">
              {{ chain.enabled ? '暂无存证记录' : '当前为演示模式，未接入区块链' }}
            </div>
            <div class="chain-empty-desc" v-if="!chain.enabled">
              业务数据已正常落库，存证环节被标记为 <code>SKIPPED</code> 并如实留痕，
              不生成虚假交易哈希。将 <code>application.yml</code> 中 <code>chain.enabled</code>
              设为 <code>true</code> 并配置节点证书与合约地址后，新建的业务记录会实时上链。
            </div>
            <div class="chain-empty-desc" v-else>
              新建批次或登记农事、采收、质检等业务节点后，系统会自动写入链上并存证。
            </div>
          </div>
        </template>
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

    <el-dialog v-model="verifyVisible" title="链上链下一致性校验" width="800px">
      <div class="verify-input">
        <el-input v-model="batchNo" placeholder="输入批次号，如 AG20260512001" @keyup.enter="doVerify" />
        <el-button type="primary" :loading="verifying" @click="doVerify">开始校验</el-button>
      </div>

      <template v-if="result">
        <div class="verify-head" :class="result.passed ? 'pass' : 'fail'">
          <el-icon><component :is="result.passed ? 'CircleCheck' : 'CircleClose'" /></el-icon>
          <div>
            <div class="v-title">{{ result.passed ? '校验通过' : '校验未通过' }}</div>
            <div class="v-sub">{{ result.message }}</div>
          </div>
          <div class="v-count">
            <b>{{ result.matchedRecords }}</b> / {{ result.totalRecords }}
          </div>
        </div>

        <el-table :data="result.items" size="small">
          <el-table-column prop="name" label="存证记录" min-width="150" />
          <el-table-column label="链下摘要" min-width="150">
            <template #default="{ row }"><span class="mono">{{ short(row.localHash) }}</span></template>
          </el-table-column>
          <el-table-column label="链上摘要" min-width="150">
            <template #default="{ row }"><span class="mono">{{ short(row.chainHash) }}</span></template>
          </el-table-column>
          <el-table-column label="摘要" width="90">
            <template #default="{ row }">
              <el-tag :type="row.hashMatched ? 'success' : 'danger'" size="small">{{ row.hashMatched ? '一致' : '不一致' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回执" width="80">
            <template #default="{ row }">
              <el-tag :type="row.receiptOk ? 'success' : 'danger'" size="small">{{ row.receiptOk ? '正常' : '异常' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Link, Search } from '@element-plus/icons-vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { chainPage, chainStatus, chainVerifyByNo } from '@/api'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, batchNo: '', status: '' })
const chain = ref({ enabled: false, ready: false, blockNumber: null })

const verifyVisible = ref(false)
const verifying = ref(false)
const batchNo = ref('')
const result = ref(null)

const short = (hash) => (hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—')

const load = async () => {
  loading.value = true
  try {
    const res = await chainPage({
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

const doVerify = async () => {
  if (!batchNo.value) return ElMessage.warning('请输入批次号')
  verifying.value = true
  try {
    const res = await chainVerifyByNo(batchNo.value.trim())
    result.value = res.data
  } catch (e) {
    result.value = null
  } finally {
    verifying.value = false
  }
}

onMounted(async () => {
  load()
  try {
    const res = await chainStatus()
    chain.value = res.data
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

.chain-state {
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
}

.verify-input {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.verify-head {
  display: flex;
  align-items: center;
  gap: 14px;
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
}

.v-count {
  margin-left: auto;
  font-size: 13px;
}

.v-count b {
  font-size: 22px;
}

.chain-empty {
  padding: 26px 16px 32px;
  text-align: center;
  line-height: 1.9;
}

.chain-empty-icon {
  font-size: 34px;
  color: #c4d3ca;
  margin-bottom: 6px;
}

.chain-empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #2c3b34;
  margin-bottom: 6px;
}

.chain-empty-desc {
  font-size: 13px;
  color: #7b8c84;
  max-width: 640px;
  margin: 0 auto;
}

.chain-empty-desc code {
  background: #eef4f0;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  color: #2f7a52;
}
</style>
