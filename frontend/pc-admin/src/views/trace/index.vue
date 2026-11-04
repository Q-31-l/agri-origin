<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">溯源查询</h1>
        <div class="page-desc">输入溯源码或批次号，查看完整流转履历、质检摘要与链上凭证</div>
      </div>
    </div>

    <div class="search-card">
      <div class="search-inner">
        <el-input
          v-model="code"
          size="large"
          placeholder="请输入溯源码（TC 开头）或批次号（AG 开头）"
          clearable
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" :loading="loading" @click="search">查询溯源</el-button>
      </div>
      <div class="samples">
        <span class="muted">演示数据：</span>
        <el-link v-for="s in samples" :key="s" type="primary" :underline="false" @click="quick(s)">{{ s }}</el-link>
      </div>
    </div>

    <template v-if="data">
      <div class="result-head">
        <div class="rh-left">
          <div class="rh-tag">溯源结果</div>
          <h2>{{ data.productName }}<span v-if="data.variety"> · {{ data.variety }}</span></h2>
          <div class="rh-meta">
            <span>批次 {{ data.batchNo }}</span>
            <span class="dot-sep">·</span>
            <span>{{ data.origin }}</span>
          </div>
        </div>
        <div class="rh-right">
          <StatusTag :status="data.status" />
          <ChainBadge :status="data.chainStatus" />
          <el-button :icon="Link" @click="doVerify">存证校验</el-button>
        </div>
      </div>

      <el-alert
        v-if="data.abnormalFlag === 1"
        type="error"
        show-icon
        :closable="false"
        title="该批次检测不合格，已禁止销售流转"
        description="如需了解处置进展，请联系属地监管部门。"
        style="margin-bottom: 16px"
      />

      <div class="grid">
        <div class="card">
          <div class="card-title">批次概要</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="产品">{{ data.productName }}</el-descriptions-item>
            <el-descriptions-item label="产量">{{ data.quantity }} {{ data.unit }}</el-descriptions-item>
            <el-descriptions-item label="产地">{{ data.villageName }}</el-descriptions-item>
            <el-descriptions-item label="种植户">{{ data.farmerName }}</el-descriptions-item>
            <el-descriptions-item label="种植日期">{{ data.plantingDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="采收日期">{{ data.harvestDate || '—' }}</el-descriptions-item>
            <el-descriptions-item label="质检结论" :span="2">
              <el-tag :type="data.qualityResult === 'PASS' ? 'success' : data.qualityResult === 'FAIL' ? 'danger' : 'info'">
                {{ data.qualityResult === 'PASS' ? '合格' : data.qualityResult === 'FAIL' ? '不合格' : '暂无' }}
              </el-tag>
              <span class="muted" style="margin-left: 10px">
                {{ data.qualityReportNo || '' }} {{ data.qualityOrg ? '· ' + data.qualityOrg : '' }}
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="card chain-card">
          <div class="card-title">链上凭证</div>
          <div class="chain-row">
            <span class="muted">存证状态</span>
            <ChainBadge :status="data.chainStatus" />
          </div>
          <div class="chain-row">
            <span class="muted">交易哈希</span>
            <span class="mono hash">{{ data.chainTxHash || '未上链' }}</span>
          </div>
          <div class="chain-row">
            <span class="muted">区块高度</span>
            <span class="mono">{{ data.blockNumber || '—' }}</span>
          </div>
          <div class="chain-row">
            <span class="muted">链服务</span>
            <span>{{ data.chainEnabled ? '已启用' : '演示模式（未上链）' }}</span>
          </div>
          <div v-if="!data.chainEnabled" class="chain-tip">{{ data.chainMessage }}</div>
        </div>
      </div>

      <div class="card">
        <div class="card-title">流转履历</div>
        <div class="card-sub">按时间顺序展示从种植到销售的每一个关键节点</div>
        <el-timeline class="timeline">
          <el-timeline-item
            v-for="(item, index) in data.timeline"
            :key="index"
            :timestamp="item.time"
            placement="top"
            :color="item.chainStatus === 'SUCCESS' ? '#2f9e63' : '#c3ccd6'"
            :hollow="item.chainStatus !== 'SUCCESS'"
          >
            <div class="tl-card">
              <div class="tl-head">
                <span class="tl-title">{{ item.title }}</span>
                <ChainBadge :status="item.chainStatus" />
              </div>
              <div class="tl-detail">{{ item.detail }}</div>
              <div class="tl-foot">
                <span class="muted">{{ item.operator || '—' }}</span>
                <span v-if="item.txHash" class="mono hash">{{ short(item.txHash) }}</span>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </template>

    <el-empty v-else description="输入溯源码后展示溯源结果" class="empty" />

    <el-dialog v-model="verifyVisible" title="存证校验结果" width="560px">
      <div class="verify-box" :class="verify.passed ? 'pass' : 'fail'">
        <el-icon><component :is="verify.passed ? 'CircleCheck' : 'CircleClose'" /></el-icon>
        <div>
          <div class="v-title">{{ verify.passed ? '校验通过' : '校验未通过' }}</div>
          <div class="v-sub">{{ verify.message }}</div>
        </div>
      </div>
      <div class="verify-meta">
        <span>存证记录：{{ verify.totalRecords || 0 }} 条</span>
        <span>校验通过：{{ verify.matchedRecords || 0 }} 条</span>
        <span>校验时间：{{ verify.verifiedAt }}</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Link, Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { traceQuery, traceVerify } from '@/api'

const route = useRoute()
const code = ref('')
const loading = ref(false)
const data = ref(null)
const verifyVisible = ref(false)
const verify = ref({ passed: false, message: '' })

const samples = ['TC20260512001', 'AG20260515002', 'TC20260601003']

const short = (hash) => (hash ? hash.slice(0, 10) + '…' + hash.slice(-6) : '—')

const search = async () => {
  const value = code.value.trim()
  if (!value) return ElMessage.warning('请输入溯源码或批次号')
  loading.value = true
  try {
    const res = await traceQuery(value)
    data.value = res.data
  } catch (e) {
    data.value = null
  } finally {
    loading.value = false
  }
}

const quick = (value) => {
  code.value = value
  search()
}

const doVerify = async () => {
  const res = await traceVerify(data.value.traceCode)
  verify.value = res.data
  verifyVisible.value = true
}

onMounted(() => {
  if (route.query.code) {
    code.value = String(route.query.code)
    search()
  }
})
</script>

<style scoped>
.search-card {
  background: #fff;
  border-radius: 14px;
  padding: 24px;
  box-shadow: var(--shadow);
  margin-bottom: 18px;
}

.search-inner {
  display: flex;
  gap: 12px;
  max-width: 760px;
}

.search-inner :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.samples {
  margin-top: 12px;
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 12px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.rh-tag {
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--primary);
  font-weight: 600;
}

.result-head h2 {
  margin: 6px 0;
  font-size: 24px;
}

.rh-meta {
  font-size: 13px;
  color: var(--text-secondary);
}

.dot-sep {
  margin: 0 8px;
  color: #c3ccd6;
}

.rh-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 12px;
}

.card-sub {
  font-size: 12px;
  color: var(--text-secondary);
  margin: -6px 0 14px;
}

.chain-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
  font-size: 13px;
}

.chain-row:last-of-type {
  border-bottom: none;
}

.chain-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #8b9aaa;
  background: #f7faf8;
  border-radius: 8px;
  padding: 8px 10px;
}

.timeline {
  margin-top: 10px;
}

.tl-card {
  background: #fbfdfc;
  border: 1px solid #edf2ef;
  border-radius: 10px;
  padding: 14px 16px;
}

.tl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tl-title {
  font-weight: 600;
}

.tl-detail {
  font-size: 13px;
  color: #4a5a6a;
  margin-top: 8px;
  line-height: 1.7;
}

.tl-foot {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
}

.verify-box {
  display: flex;
  gap: 14px;
  align-items: center;
  border-radius: 12px;
  padding: 16px 18px;
}

.verify-box.pass {
  background: #eef9f3;
  color: #1f7a4c;
}

.verify-box.fail {
  background: #fdeeee;
  color: #b93b3b;
}

.verify-box .el-icon {
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

.verify-meta {
  display: flex;
  gap: 18px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-secondary);
}

.empty {
  margin-top: 40px;
}
</style>
