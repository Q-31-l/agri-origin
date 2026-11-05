<template>
  <pc-shell title="数据看板">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">数据看板</text>
          <text class="pc-sub">
            数据范围：{{ scopeText }} · 更新于 {{ updatedAt }}
          </text>
        </view>
        <view class="pc-acts">
          <view class="pc-btn ghost" @click="load">刷新数据</view>
        </view>
      </view>

      <!-- 核心指标 -->
      <view class="pc-stats">
        <view class="pc-stat">
          <text class="pc-stat-label">批次总数</text>
          <text class="pc-stat-value">{{ s.totalBatches }}</text>
          <text class="pc-stat-hint">今日新增 {{ s.todayBatches }} 个</text>
        </view>
        <view class="pc-stat">
          <text class="pc-stat-label">异常批次</text>
          <text class="pc-stat-value red">{{ s.abnormalBatches }}</text>
          <text class="pc-stat-hint">占全部 {{ pct(s.abnormalBatches) }}%</text>
        </view>
        <view class="pc-stat">
          <text class="pc-stat-label">质检合格率</text>
          <text class="pc-stat-value green">{{ s.qualifiedRate }}%</text>
          <text class="pc-stat-hint">报告 {{ s.totalReports }} 份</text>
        </view>
        <view class="pc-stat">
          <text class="pc-stat-label">上链成功率</text>
          <text class="pc-stat-value" :class="s.chainSuccessRate >= 99 ? 'green' : 'orange'">
            {{ s.chainSuccessRate }}%
          </text>
          <text class="pc-stat-hint">存证 {{ s.totalChainRecords }} 条</text>
        </view>
      </view>

      <!-- 状态分布 + 村级分布 -->
      <view class="pc-cols">
        <view class="pc-col-main">
          <view class="pc-card">
            <view class="pc-card-head">
              <text class="pc-card-title">批次状态分布</text>
              <text class="mini">共 {{ s.totalBatches }} 个批次</text>
            </view>
            <view class="pc-card-body">
              <view v-if="statusRows.length" class="pc-ratio">
                <view v-for="r in statusRows" :key="r.name" class="pc-ratio-row">
                  <text class="pc-ratio-name">{{ r.label }}</text>
                  <view class="pc-ratio-track">
                    <view
                      class="pc-ratio-fill"
                      :style="{ width: r.pct + '%', background: r.color }"
                    ></view>
                  </view>
                  <text class="pc-ratio-num">{{ r.value }}</text>
                </view>
              </view>
              <view v-else class="pc-none">暂无批次数据</view>
            </view>
          </view>
        </view>

        <view class="pc-col-side">
          <view class="pc-card">
            <view class="pc-card-head">
              <text class="pc-card-title">村级分布</text>
              <text class="mini">Top 6</text>
            </view>
            <view class="pc-card-body">
              <view v-if="villageRows.length" class="pc-ratio">
                <view v-for="r in villageRows" :key="r.name" class="pc-ratio-row">
                  <text class="pc-ratio-name" :title="r.name">{{ r.name }}</text>
                  <view class="pc-ratio-track">
                    <view class="pc-ratio-fill" :style="{ width: r.pct + '%' }"></view>
                  </view>
                  <text class="pc-ratio-num">{{ r.value }}</text>
                </view>
              </view>
              <view v-else class="pc-none">暂无村级数据</view>
            </view>
          </view>
        </view>
      </view>

      <!-- 趋势 + 链上 -->
      <view class="pc-cols">
        <view class="pc-col-main">
          <view class="pc-card">
            <view class="pc-card-head">
              <text class="pc-card-title">近 7 日批次新增</text>
            </view>
            <view class="pc-card-body">
              <view v-if="trendRows.length" class="pc-chart">
                <view v-for="t in trendRows" :key="t.name" class="pc-bar-col">
                  <text class="pc-bar-val">{{ t.value }}</text>
                  <view class="pc-bar" :style="{ height: t.h + '%' }"></view>
                  <text class="pc-bar-label">{{ t.name }}</text>
                </view>
              </view>
              <view v-else class="pc-none">近 7 日暂无新增批次</view>
            </view>
          </view>
        </view>

        <view class="pc-col-side">
          <view class="pc-card">
            <view class="pc-card-head">
              <text class="pc-card-title">链上存证概况</text>
            </view>
            <view class="pc-card-body">
              <view class="pc-kv">
                <text class="pc-k">成功上链</text>
                <text class="pc-v green-text">{{ s.chainSuccess }} 条</text>
              </view>
              <view class="pc-kv">
                <text class="pc-k">上链失败</text>
                <text class="pc-v red-text">{{ s.chainFailed }} 条</text>
              </view>
              <view class="pc-kv">
                <text class="pc-k">演示跳过</text>
                <text class="pc-v orange-text">{{ s.chainSkipped }} 条</text>
              </view>
              <view class="pc-kv">
                <text class="pc-k">合约存证总量</text>
                <text class="pc-v">{{ s.totalChainRecords }} 条</text>
              </view>
              <view class="pc-kv">
                <text class="pc-k">覆盖农户</text>
                <text class="pc-v">{{ s.totalFarmers }} 人</text>
              </view>
              <view class="pc-kv">
                <text class="pc-k">覆盖行政村</text>
                <text class="pc-v">{{ s.totalVillages }} 个</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 全流程推进概览 -->
      <view class="pc-card">
        <view class="pc-card-head">
          <text class="pc-card-title">全流程推进概览</text>
        </view>
        <view class="pc-card-body">
          <view class="stage">
            <view v-for="st in stages" :key="st.key" class="stage-item">
              <view class="stage-dot" :style="{ background: st.color }"></view>
              <text class="stage-name">{{ st.label }}</text>
              <text class="stage-num">{{ s[st.key] }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useRealtime } from '@/composables/useRealtime'
import PcShell from '@/layouts/pc-shell.vue'
import { statsOverview } from '@/api'
import { useUserStore } from '@/store/user'
import { BATCH_STATUS, ROLE_NAME } from '@/config/constants'
import { toast } from '@/utils/ui'

const store = useUserStore()

const s = ref({
  totalBatches: 0,
  plantingBatches: 0,
  qualifiedBatches: 0,
  inTransitBatches: 0,
  onSaleBatches: 0,
  soldBatches: 0,
  abnormalBatches: 0,
  todayBatches: 0,
  totalFarmers: 0,
  totalVillages: 0,
  totalReports: 0,
  totalChainRecords: 0,
  chainSuccess: 0,
  chainFailed: 0,
  chainSkipped: 0,
  qualifiedRate: 0,
  chainSuccessRate: 0,
  statusDistribution: [],
  villageDistribution: [],
  trend: []
})

const updatedAt = ref('—')

const scopeText = computed(() => {
  const role = store.roleCode
  if (role === 'FARMER') return '仅本人农户批次'
  if (role === 'VILLAGE_ADMIN') return '本行政村范围'
  return '全平台数据'
})

const stages = [
  { key: 'plantingBatches', label: '种植中', color: '#2563eb' },
  { key: 'qualifiedBatches', label: '检测合格', color: '#16a34a' },
  { key: 'inTransitBatches', label: '运输中', color: '#7c3aed' },
  { key: 'onSaleBatches', label: '销售中', color: '#c2410c' },
  { key: 'soldBatches', label: '已售罄', color: '#475569' },
  { key: 'abnormalBatches', label: '异常管控', color: '#d93026' }
]

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

const statusRows = computed(() => {
  const list = (s.value.statusDistribution || []).map((i) => {
    const conf = BATCH_STATUS[i.name] || { text: i.name, color: '#94a3b8' }
    return { name: i.name, label: conf.text, value: num(i.value), color: conf.color }
  })
  const max = Math.max(1, ...list.map((i) => i.value))
  return list
    .sort((a, b) => b.value - a.value)
    .map((i) => ({ ...i, pct: Math.round((i.value / max) * 100) }))
})

const villageRows = computed(() => {
  const list = (s.value.villageDistribution || [])
    .map((i) => ({ name: i.name || '未知', value: num(i.value) }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 6)
  const max = Math.max(1, ...list.map((i) => i.value))
  return list.map((i) => ({ ...i, pct: Math.round((i.value / max) * 100) }))
})

const trendRows = computed(() => {
  const list = (s.value.trend || []).map((i) => ({
    name: i.name,
    value: num(i.value)
  }))
  const max = Math.max(1, ...list.map((i) => i.value))
  return list.map((i) => ({ ...i, h: Math.max(3, Math.round((i.value / max) * 100)) }))
})

function pct(v) {
  const total = num(s.value.totalBatches)
  if (!total) return '0.0'
  return ((num(v) * 100) / total).toFixed(1)
}

async function load() {
  try {
    const res = await statsOverview()
    s.value = { ...s.value, ...(res.data || {}) }
    const d = new Date()
    const p = (n) => String(n).padStart(2, '0')
    updatedAt.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  } catch (e) {
    toast(e && e.message ? e.message : '统计数据加载失败')
  }
}

onShow(load)

// 小程序 / App 端写入业务数据后，PC 看板自动刷新
useRealtime(load, { types: ['batch', 'abnormal'] })
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.mini {
  font-size: 11px;
  color: #94a3b8;
}
.green-text {
  color: #16a34a;
}
.red-text {
  color: #d93026;
}
.orange-text {
  color: #c2410c;
}
.stage {
  display: flex;
  flex-direction: row;
  gap: 10px;
}
.stage-item {
  flex: 1;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  background: #fbfcfd;
}
.stage-dot {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  margin-bottom: 8px;
}
.stage-name {
  font-size: 11px;
  color: #94a3b8;
}
.stage-num {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin-top: 2px;
}
</style>
