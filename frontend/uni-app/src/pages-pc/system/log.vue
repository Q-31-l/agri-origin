<template>
  <pc-shell title="操作日志">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">操作日志</text>
          <text class="pc-sub">全平台关键操作留痕，含请求参数、耗时与异常信息</text>
        </view>
        <view class="pc-acts">
          <view class="pc-btn ghost" @click="load">刷新</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.username"
            placeholder="按操作人查询"
            @confirm="search"
          />
          <input
            class="pc-input"
            v-model="query.module"
            placeholder="按模块查询，如 批次"
            @confirm="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-time">时间</text>
          <text class="pc-th">操作人</text>
          <text class="pc-th">角色</text>
          <text class="pc-th">模块</text>
          <text class="pc-th">动作</text>
          <text class="pc-th w-uri">请求地址</text>
          <text class="pc-th">方法</text>
          <text class="pc-th">IP</text>
          <text class="pc-th">耗时</text>
          <text class="pc-th w-status">状态</text>
          <text class="pc-th w-act">详情</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无日志数据</view>
        <view v-else class="pc-tbody">
          <view v-for="l in list" :key="l.id" class="pc-tr">
            <text class="pc-td w-time">{{ formatDateTime(l.createTime) }}</text>
            <text class="pc-td">{{ l.username || '-' }}</text>
            <text class="pc-td">{{ roleName(l.roleCode) }}</text>
            <text class="pc-td">{{ l.module || '-' }}</text>
            <text class="pc-td">{{ l.action || '-' }}</text>
            <text class="pc-td w-uri mono">{{ l.requestUri || '-' }}</text>
            <text class="pc-td">{{ l.method || '-' }}</text>
            <text class="pc-td mono">{{ l.ip || '-' }}</text>
            <text class="pc-td">{{ l.costMs != null ? l.costMs + 'ms' : '-' }}</text>
            <view class="pc-td w-status">
              <text class="st" :class="l.status === 1 ? 'ok' : 'bad'">
                {{ l.status === 1 ? '成功' : '失败' }}
              </text>
            </view>
            <view class="pc-td w-act">
              <text class="pc-link" @click="openDetail(l)">查看</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal v-model="detailVisible" title="日志详情" width="640px" hide-footer>
        <view class="pc-form">
          <view class="pc-kv"><text class="pc-k">操作时间</text><text class="pc-v">{{ formatDateTime(current.createTime) }}</text></view>
          <view class="pc-kv"><text class="pc-k">操作人</text><text class="pc-v">{{ current.username }} · {{ roleName(current.roleCode) }}</text></view>
          <view class="pc-kv"><text class="pc-k">模块 / 动作</text><text class="pc-v">{{ current.module }} · {{ current.action }}</text></view>
          <view class="pc-kv"><text class="pc-k">请求地址</text><text class="pc-v mono">{{ current.method }} {{ current.requestUri }}</text></view>
          <view class="pc-kv"><text class="pc-k">客户端 IP</text><text class="pc-v mono">{{ current.ip }}</text></view>
          <view class="pc-kv"><text class="pc-k">耗时</text><text class="pc-v">{{ current.costMs }} ms</text></view>
          <view class="pc-kv"><text class="pc-k">状态</text><text class="pc-v">{{ current.status === 1 ? '成功' : '失败' }}</text></view>
          <view class="pc-field full">
            <text class="pc-label">请求参数</text>
            <view class="code-box">{{ current.params || '（无）' }}</view>
          </view>
          <view v-if="current.errorMsg" class="pc-field full">
            <text class="pc-label">异常信息</text>
            <view class="code-box err">{{ current.errorMsg }}</view>
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import { logPage } from '@/api'
import { ROLE_NAME } from '@/config/constants'
import { toast } from '@/utils/ui'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({ page: 1, size: 15, username: '', module: '' })

function roleName(code) {
  return ROLE_NAME[code] || code || '-'
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.username) params.username = query.username.trim()
    if (query.module) params.module = query.module.trim()
    const res = await logPage(params)
    list.value = (res.data && res.data.records) || []
    total.value = (res.data && res.data.total) || 0
  } catch (e) {
    toast(e && e.message ? e.message : '加载失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.username = ''
  query.module = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

const detailVisible = ref(false)
const current = ref({})

function openDetail(l) {
  current.value = l
  detailVisible.value = true
}

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-time { flex: 1.9; }
.w-uri { flex: 1.7; }
.w-status { flex: 0.7; }
.w-act { flex: 0.6; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11px;
}
.st {
  font-size: 10.5px;
  border-radius: 3px;
  padding: 1px 6px;
}
.st.ok {
  color: #16a34a;
  background: #e8f7ee;
}
.st.bad {
  color: #d93026;
  background: #fdeceb;
}
.code-box {
  background: #f8fafc;
  border: 1px solid #eef2f6;
  border-radius: 6px;
  padding: 10px;
  font-size: 11.5px;
  color: #4b5563;
  font-family: Consolas, monospace;
  max-height: 180px;
  overflow-y: auto;
  word-break: break-all;
  line-height: 1.6;
}
.code-box.err {
  color: #d93026;
  background: #fdeceb;
  border-color: #f7c9c5;
}
</style>
