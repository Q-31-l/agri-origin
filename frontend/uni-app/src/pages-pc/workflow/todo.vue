<template>
  <pc-shell title="审核待办">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <view class="title-row">
            <text class="pc-title">审核待办</text>
            <text class="cnt-chip">{{ total }} 项</text>
          </view>
          <text class="pc-sub">只显示确实轮到你处理的数据：提交人、提交时间、归属村一并列出</text>
        </view>
        <view class="pc-acts">
          <view class="pc-btn ghost" @click="load">刷新</view>
        </view>
      </view>

      <view v-if="loading" class="pc-none">加载中…</view>
      <view v-else-if="!groups.length" class="pc-none">暂无待办。有新的提交时会在这里出现，并随实时事件自动刷新</view>

      <view v-for="g in groups" :key="g.type" class="pc-card group-card">
        <view class="pc-card-head">
          <text class="pc-card-title">{{ g.title }}</text>
          <text class="g-count">{{ g.count }} 条待处理</text>
        </view>
        <view class="pc-card-body">
          <view class="row head-row">
            <text class="c1">批次 / 产品</text>
            <text class="c2">提交内容</text>
            <text class="c3">提交人</text>
            <text class="c4">提交时间</text>
            <text class="c5">操作</text>
          </view>
          <view v-for="it in g.items" :key="it.id" class="row body-row">
            <view class="c1">
              <text class="p-name">{{ it.productName || '—' }}</text>
              <text class="p-no mono">{{ it.batchNo }}</text>
            </view>
            <view class="c2">
              <text class="s-sum">{{ it.summary }}</text>
              <text v-if="it.from" class="s-from">{{ it.from }}</text>
            </view>
            <text class="c3">{{ it.submitBy || '—' }}</text>
            <text class="c4">{{ fmt(it.submitTime) }}</text>
            <view class="c5">
              <template v-if="g.type === 'ABNORMAL'">
                <text class="pc-link" @click="nav(ROUTES.PC_ABNORMAL)">去处置</text>
              </template>
              <template v-else>
                <text class="pc-link" @click="openBatch(it)">查看</text>
                <text class="pc-link ok" @click="doAudit(g, it, 'APPROVED')">通过</text>
                <text class="pc-link danger" @click="doAudit(g, it, 'REJECTED')">驳回</text>
              </template>
            </view>
          </view>
        </view>
      </view>
    </view>
  </pc-shell>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import { workflowTodo, auditBatch, auditPlanting, auditHarvest, auditQuality, auditLogistics, auditSales } from '@/api'
import { nav } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { toast } from '@/utils/ui'

const loading = ref(true)
const total = ref(0)
const groups = ref([])

const AUDIT_API = {
  BATCH: auditBatch,
  PLANTING: auditPlanting,
  HARVEST: auditHarvest,
  QUALITY: auditQuality,
  LOGISTICS: auditLogistics,
  SALES: auditSales
}

async function load() {
  loading.value = true
  try {
    const res = await workflowTodo()
    const d = (res && res.data) || {}
    total.value = d.total || 0
    groups.value = (d.groups || []).filter((g) => g.items && g.items.length)
  } catch (e) {
    groups.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function fmt(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

function openBatch(it) {
  if (it.batchId) nav(ROUTES.PC_BATCH_DETAIL, { id: it.batchId })
}

async function doAudit(group, item, status) {
  const api = AUDIT_API[group.type]
  if (!api) return
  let remark = ''
  if (status === 'REJECTED') {
    const r = await new Promise((resolve) => {
      uni.showModal({
        title: '驳回并说明原因',
        editable: true,
        placeholderText: '请写明驳回原因，提交人会看到这条意见',
        success: (res) => resolve(res),
        fail: () => resolve({ confirm: false })
      })
    })
    if (!r.confirm) return
    remark = (r.content || '').trim()
    if (!remark) return toast('驳回必须填写原因')
  } else {
    const r = await new Promise((resolve) => {
      uni.showModal({
        title: '确认通过',
        content: '通过后该记录进入下一环节，操作会记入审核留痕',
        success: (res) => resolve(res.confirm),
        fail: () => resolve(false)
      })
    })
    if (!r) return
  }
  try {
    await api(item.id, { auditStatus: status, auditRemark: remark })
    uni.showToast({ title: status === 'APPROVED' ? '已通过' : '已驳回', icon: 'success' })
    await load()
  } catch (e) {
    // request 层已提示
  }
}

onShow(() => load())
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.mono {
  font-family: Consolas, monospace;
}
.cnt-chip {
  font-size: 11px;
  color: #b45309;
  background: #fff3dd;
  border: 1px solid #f3dfb9;
  border-radius: 4px;
  padding: 1px 8px;
}
.group-card {
  margin-bottom: 14px;
}
.group-card .pc-card-body {
  padding: 6px 18px 14px;
}
.g-count {
  font-size: 11.5px;
  color: #b45309;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
}
.head-row {
  padding: 8px 0;
  border-bottom: 1px solid #eef1f4;
  font-size: 11.5px;
  color: #94a3b8;
}
.body-row {
  padding: 10px 0;
  border-bottom: 1px dashed #f0f3f6;
  font-size: 12.5px;
  color: #334155;
}
.body-row:last-child {
  border-bottom: none;
}
.c1 {
  width: 190px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.c2 {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.c3 {
  width: 90px;
  flex-shrink: 0;
}
.c4 {
  width: 130px;
  flex-shrink: 0;
  color: #64748b;
}
.c5 {
  width: 132px;
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  gap: 12px;
  justify-content: flex-end;
}
.p-name {
  font-weight: 600;
  color: #0f172a;
}
.p-no {
  font-size: 11px;
  color: #94a3b8;
}
.s-sum {
  color: #334155;
}
.s-from {
  font-size: 11px;
  color: #94a3b8;
}
.pc-link.ok {
  color: #1f7a4d;
}
.pc-link.danger {
  color: #d93026;
}
</style>
