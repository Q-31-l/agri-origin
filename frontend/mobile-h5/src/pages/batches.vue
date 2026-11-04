<template>
  <div class="page">
    <div class="head">
      <div class="head-title">我的批次</div>
      <div class="head-sub">共 {{ total }} 个批次</div>
    </div>

    <van-search v-model="keyword" placeholder="搜索批次号 / 产品" shape="round" background="transparent" @search="reload" />

    <van-tabs v-model:active="active" background="transparent" line-width="22" color="#2f9e63" @change="onTab">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.label" :name="t.value" />
    </van-tabs>

    <div class="list">
      <div v-for="b in rows" :key="b.id" class="batch" @click="$router.push(`/batch/${b.id}`)">
        <div class="row">
          <div class="no">{{ b.batchNo }}</div>
          <span class="status" :class="b.status">{{ statusLabel(b.status) }}</span>
        </div>
        <div class="name">{{ b.productName }}<span v-if="b.variety"> · {{ b.variety }}</span></div>
        <div class="meta">
          <span>{{ b.quantity }}{{ b.unit }}</span>
          <span class="dot">·</span>
          <span>{{ b.farmName || b.villageName }}</span>
        </div>
        <div class="foot">
          <span class="muted">种植 {{ b.plantingDate || '—' }}</span>
          <span class="chain" :class="b.chainStatus">{{ chainLabel(b.chainStatus) }}</span>
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && !rows.length" description="暂无批次" />

    <div class="more" v-if="hasMore">
      <van-button size="small" plain round :loading="loading" @click="loadMore">加载更多</van-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { batchPage } from '@/api'

const keyword = ref('')
const active = ref('')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)

const tabs = [
  { label: '全部', value: '' },
  { label: '种植中', value: 'PLANTING' },
  { label: '已采收', value: 'HARVESTED' },
  { label: '质检合格', value: 'QUALIFIED' },
  { label: '异常', value: 'ABNORMAL' }
]

const statusLabel = (s) =>
  ({
    PLANTING: '种植中',
    HARVESTED: '已采收',
    QUALIFIED: '质检合格',
    IN_TRANSIT: '运输中',
    ON_SALE: '在售',
    SOLD: '已售出',
    ABNORMAL: '异常',
    CLOSED: '已结案'
  }[s] || s)

const chainLabel = (s) => ({ SUCCESS: '已上链', FAILED: '上链失败', SKIPPED: '未上链' }[s] || '未上链')

const fetch = async (reset = false) => {
  loading.value = true
  try {
    if (reset) page.value = 1
    const res = await batchPage({
      page: page.value,
      size: 10,
      keyword: keyword.value || undefined,
      status: active.value || undefined
    })
    const list = res.data.records || []
    rows.value = reset ? list : [...rows.value, ...list]
    total.value = res.data.total || 0
    hasMore.value = rows.value.length < total.value
  } finally {
    loading.value = false
  }
}

const reload = () => fetch(true)
const loadMore = () => {
  page.value += 1
  fetch(false)
}
const onTab = () => reload()

onMounted(reload)
</script>

<style scoped>
.head {
  padding: 24px 20px 6px;
}

.head-title {
  font-size: 22px;
  font-weight: 700;
}

.head-sub {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.list {
  padding: 8px 14px 0;
}

.batch {
  background: #fff;
  border-radius: 14px;
  padding: 15px 16px;
  margin-bottom: 10px;
  box-shadow: 0 4px 14px rgba(31, 61, 45, 0.04);
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
}

.status {
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef4f0;
  color: #2f9e63;
}

.status.ABNORMAL {
  background: #fdeeee;
  color: #e05252;
}

.status.HARVESTED {
  background: #fff6e8;
  color: #d99a2b;
}

.status.SOLD,
.status.CLOSED {
  background: #f1f4f7;
  color: #7b8a99;
}

.name {
  font-size: 15px;
  font-weight: 600;
  margin-top: 10px;
}

.meta {
  font-size: 12px;
  color: #5a6b7b;
  margin-top: 6px;
}

.meta .dot {
  margin: 0 6px;
  color: #c3ccd6;
}

.foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f5;
}

.chain {
  font-size: 10px;
  color: #8b9aaa;
}

.chain.SUCCESS {
  color: #2f9e63;
}

.chain.FAILED {
  color: #e05252;
}

.more {
  text-align: center;
  padding: 12px 0 20px;
}
</style>
