<template>
  <div class="page">
    <div class="hero">
      <div class="hero-text">
        <div class="hero-tag">全流程可信溯源</div>
        <h1>早上好，{{ store.displayName }}</h1>
        <p>
          当前共管理 <b>{{ stats.totalBatches }}</b> 个农产品批次，其中
          <b class="warn">{{ stats.abnormalBatches }}</b> 个处于异常管控，
          链上存证成功率 <b>{{ stats.chainSuccessRate }}%</b>。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/batch')">批次管理</el-button>
          <el-button plain @click="$router.push('/trace')">溯源查询</el-button>
        </div>
      </div>
      <div class="hero-panel">
        <div class="hp-row">
          <span>链服务</span>
          <span class="hp-value" :class="{ ok: chain.enabled && chain.ready }">
            {{ chain.enabled ? (chain.ready ? '已连接' : '异常') : '演示模式' }}
          </span>
        </div>
        <div class="hp-row">
          <span>当前区块</span>
          <span class="hp-value mono">{{ chain.blockNumber || '—' }}</span>
        </div>
        <div class="hp-row">
          <span>存证总数</span>
          <span class="hp-value">{{ stats.totalChainRecords || 0 }}</span>
        </div>
        <div class="hp-row">
          <span>质检合格率</span>
          <span class="hp-value">{{ stats.qualifiedRate || 0 }}%</span>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <StatCard label="批次总数" :value="stats.totalBatches || 0" suffix="个" icon="Box" hint="全部农产品批次" />
      <StatCard label="今日新建" :value="stats.todayBatches || 0" suffix="个" icon="Calendar" color="#3b82f6" hint="今日建档批次" />
      <StatCard label="异常批次" :value="stats.abnormalBatches || 0" suffix="个" icon="Warning" color="#e05252" hint="已熔断禁售" />
      <StatCard label="上链成功率" :value="stats.chainSuccessRate || 0" suffix="%" icon="Link" color="#8b5cf6" :hint="`成功 ${stats.chainSuccess || 0} / 失败 ${stats.chainFailed || 0}`" />
    </div>

    <div class="grid">
      <div class="card chart-card">
        <div class="card-head">
          <div>
            <div class="card-title">批次状态分布</div>
            <div class="card-sub">按当前流转状态统计</div>
          </div>
        </div>
        <div ref="pieRef" class="chart"></div>
      </div>

      <div class="card chart-card">
        <div class="card-head">
          <div>
            <div class="card-title">近 7 日批次趋势</div>
            <div class="card-sub">每日新建批次数量</div>
          </div>
        </div>
        <div ref="lineRef" class="chart"></div>
      </div>
    </div>

    <div class="grid second">
      <div class="card">
        <div class="card-head">
          <div>
            <div class="card-title">各村批次分布</div>
            <div class="card-sub">基层网格化管理概览</div>
          </div>
          <el-tag round effect="plain">{{ stats.totalVillages || 0 }} 个行政村</el-tag>
        </div>
        <div ref="barRef" class="chart small"></div>
      </div>

      <div class="card">
        <div class="card-head">
          <div>
            <div class="card-title">链上存证概况</div>
            <div class="card-sub">成功 / 失败 / 未上链</div>
          </div>
          <ChainBadge :status="stats.chainFailed > 0 ? 'FAILED' : 'SUCCESS'" />
        </div>
        <div class="chain-list">
          <div class="chain-item">
            <span class="dot ok"></span>
            <div class="ci-label">存证成功</div>
            <div class="ci-value">{{ stats.chainSuccess || 0 }}</div>
          </div>
          <div class="chain-item">
            <span class="dot fail"></span>
            <div class="ci-label">存证失败</div>
            <div class="ci-value">{{ stats.chainFailed || 0 }}</div>
          </div>
          <div class="chain-item">
            <span class="dot skip"></span>
            <div class="ci-label">未上链（演示）</div>
            <div class="ci-value">{{ stats.chainSkipped || 0 }}</div>
          </div>
        </div>
        <el-alert
          v-if="!chain.enabled"
          class="tip"
          type="info"
          :closable="false"
          show-icon
          title="当前为演示模式"
          description="chain.enabled=false，业务数据仅落库并标记为未上链，系统不会显示虚假的存证成功。"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import StatCard from '@/components/StatCard.vue'
import ChainBadge from '@/components/ChainBadge.vue'
import { chainStatus, statsOverview } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const stats = ref({})
const chain = ref({ enabled: false, ready: false, blockNumber: null })

const pieRef = ref()
const lineRef = ref()
const barRef = ref()
let charts = []

const STATUS_LABEL = {
  PLANTING: '种植中',
  HARVESTED: '已采收',
  QUALIFIED: '质检合格',
  IN_TRANSIT: '运输中',
  ON_SALE: '在售',
  SOLD: '已售出',
  ABNORMAL: '异常管控',
  CLOSED: '已结案'
}

const COLORS = ['#2f9e63', '#5cb87f', '#3b82f6', '#8b5cf6', '#e6a23c', '#94a3b8', '#e05252', '#64748b']

const renderCharts = () => {
  charts.forEach((c) => c.dispose())
  charts = []

  if (pieRef.value) {
    const pie = echarts.init(pieRef.value)
    const data = (stats.value.statusDistribution || []).map((d) => ({
      name: STATUS_LABEL[d.name] || d.name,
      value: Number(d.value)
    }))
    pie.setOption({
      color: COLORS,
      tooltip: { trigger: 'item', formatter: '{b}：{c} 个（{d}%）' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 8, textStyle: { fontSize: 12 } },
      series: [
        {
          type: 'pie',
          radius: ['46%', '68%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{b}\n{c} 个' } },
          data
        }
      ]
    })
    charts.push(pie)
  }

  if (lineRef.value) {
    const line = echarts.init(lineRef.value)
    const trend = stats.value.trend || []
    line.setOption({
      grid: { left: 36, right: 20, top: 20, bottom: 30 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: trend.map((d) => d.name),
        axisLine: { lineStyle: { color: '#e2e8ef' } },
        axisLabel: { color: '#8b9aaa' }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f4f8' } },
        axisLabel: { color: '#8b9aaa' }
      },
      series: [
        {
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 7,
          data: trend.map((d) => Number(d.value)),
          lineStyle: { width: 3, color: '#2f9e63' },
          itemStyle: { color: '#2f9e63', borderWidth: 2, borderColor: '#fff' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(47,158,99,0.24)' },
              { offset: 1, color: 'rgba(47,158,99,0.01)' }
            ])
          }
        }
      ]
    })
    charts.push(line)
  }

  if (barRef.value) {
    const bar = echarts.init(barRef.value)
    const villages = stats.value.villageDistribution || []
    bar.setOption({
      grid: { left: 40, right: 16, top: 16, bottom: 30 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: villages.map((d) => d.name),
        axisLine: { lineStyle: { color: '#e2e8ef' } },
        axisLabel: { color: '#8b9aaa' }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#f0f4f8' } },
        axisLabel: { color: '#8b9aaa' }
      },
      series: [
        {
          type: 'bar',
          barWidth: 26,
          data: villages.map((d) => Number(d.value)),
          itemStyle: {
            borderRadius: [8, 8, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#5cc98c' },
              { offset: 1, color: '#2f9e63' }
            ])
          }
        }
      ]
    })
    charts.push(bar)
  }
}

const resize = () => charts.forEach((c) => c.resize())

onMounted(async () => {
  try {
    const [s, c] = await Promise.all([statsOverview(), chainStatus()])
    stats.value = s.data || {}
    chain.value = c.data || {}
  } catch (e) {
    /* 错误已由拦截器提示 */
  }
  renderCharts()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  charts.forEach((c) => c.dispose())
  charts = []
})
</script>

<style scoped>
.hero {
  background: linear-gradient(120deg, #173f2c 0%, #245f41 60%, #2f7d53 100%);
  border-radius: 16px;
  padding: 28px 32px;
  color: #eaf6ef;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(143, 230, 180, 0.22), transparent 65%);
}

.hero-tag {
  display: inline-block;
  font-size: 11px;
  letter-spacing: 2px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(143, 230, 180, 0.16);
  color: #a9edc7;
  border: 1px solid rgba(143, 230, 180, 0.24);
}

.hero h1 {
  font-size: 26px;
  margin: 14px 0 10px;
  letter-spacing: 1px;
}

.hero p {
  margin: 0;
  font-size: 13px;
  line-height: 1.9;
  color: rgba(214, 234, 223, 0.8);
}

.hero p b {
  color: #fff;
  font-size: 15px;
}

.hero p b.warn {
  color: #ffb4a8;
}

.hero-actions {
  margin-top: 22px;
  display: flex;
  gap: 10px;
}

.hero-actions :deep(.el-button--primary) {
  background: #8fe6b4;
  border-color: #8fe6b4;
  color: #12402c;
  font-weight: 600;
}

.hero-actions :deep(.el-button.is-plain) {
  background: transparent;
  color: #d9f2e4;
  border-color: rgba(255, 255, 255, 0.28);
}

.hero-panel {
  width: 260px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  padding: 14px 18px;
  z-index: 1;
  align-self: center;
}

.hp-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: rgba(214, 234, 223, 0.7);
  padding: 8px 0;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
}

.hp-row:last-child {
  border-bottom: none;
}

.hp-value {
  color: #fff;
  font-weight: 600;
}

.hp-value.ok {
  color: #8fe6b4;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 18px 0;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.grid.second {
  margin-top: 16px;
  grid-template-columns: 1.25fr 1fr;
}

.chart-card {
  padding-bottom: 8px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
}

.card-sub {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.chart {
  height: 280px;
  width: 100%;
}

.chart.small {
  height: 240px;
}

.chain-list {
  display: flex;
  gap: 12px;
  margin: 18px 0;
}

.chain-item {
  flex: 1;
  background: #f7faf8;
  border: 1px solid #edf2ef;
  border-radius: 12px;
  padding: 14px;
  position: relative;
}

.chain-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot.ok {
  background: #2f9e63;
}

.dot.fail {
  background: #e05252;
}

.dot.skip {
  background: #b6c2cf;
}

.ci-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
}

.ci-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 4px;
}

.tip {
  margin-top: 4px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .grid,
  .grid.second {
    grid-template-columns: 1fr;
  }
}
</style>
