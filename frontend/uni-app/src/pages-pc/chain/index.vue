<template>
  <pc-shell title="链上存证">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">链上存证</text>
          <text class="pc-sub">FISCO-BCOS 联盟链存证流水与链上链下一致性校验</text>
        </view>
        <view class="pc-acts">
          <view v-if="canRepair" class="pc-btn ghost" @click="doRepair">补登记未上链批次</view>
          <view class="pc-btn ghost" @click="loadAll">刷新</view>
        </view>
      </view>

      <!-- 链服务状态 -->
      <view class="chain-cards">
        <view class="cc">
          <view class="cc-top">
            <text class="cc-label">链服务状态</text>
            <text class="cc-badge" :class="chain.enabled ? 'on' : 'off'">
              {{ chain.enabled ? (chain.ready ? '运行中' : '已启用未就绪') : '演示模式' }}
            </text>
          </view>
          <text class="cc-val">{{ chain.blockNumber ? '#' + chain.blockNumber : '—' }}</text>
          <text class="cc-hint">当前区块高度</text>
        </view>
        <view class="cc">
          <view class="cc-top">
            <text class="cc-label">合约地址</text>
          </view>
          <text class="cc-val mono small">{{ chain.contractAddress || '未配置' }}</text>
          <text class="cc-hint">溯源存证合约</text>
        </view>
        <view class="cc">
          <view class="cc-top">
            <text class="cc-label">上链成功</text>
          </view>
          <text class="cc-val green">{{ stat.success }}</text>
          <text class="cc-hint">{{ stat.ready ? '成功率 ' + stat.rate + '% · 全量口径' : '统计加载中' }}</text>
        </view>
        <view class="cc">
          <view class="cc-top">
            <text class="cc-label">上链失败 / 跳过</text>
          </view>
          <text class="cc-val red">{{ stat.failed }} <text class="slash">/</text> <text class="orange">{{ stat.skipped }}</text></text>
          <text class="cc-hint">
            <text v-if="stat.ready && stat.failed > 0" class="pc-link" @click="openFailed">查看失败明细</text>
            <text v-else-if="stat.ready">无失败记录</text>
            <text v-else>统计加载中</text>
          </text>
        </view>
      </view>

      <view v-if="chain.message" class="chain-tip">{{ chain.message }}</view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.batchNo"
            placeholder="按批次编码查询存证"
            @confirm="search"
          />
          <pc-select
            v-model="query.status"
            :options="statusOptions"
            placeholder="全部状态"
            width="150px"
            @change="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th">业务类型</text>
          <text class="pc-th">事件</text>
          <text class="pc-th w-hash">交易哈希</text>
          <text class="pc-th">区块</text>
          <text class="pc-th">Gas</text>
          <text class="pc-th w-status">状态</text>
          <text class="pc-th w-time">存证时间</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无存证记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td">{{ r.bizType || '-' }}</text>
            <text class="pc-td">{{ eventName(r.eventType) }}</text>
            <text class="pc-td w-hash mono">{{ shortHash(r.txHash) }}</text>
            <text class="pc-td">{{ r.blockNumber || '-' }}</text>
            <text class="pc-td">{{ r.gasUsed || '-' }}</text>
            <view class="pc-td w-status"><pc-status :value="r.status" type="chain" /></view>
            <text class="pc-td w-time">{{ formatDateTime(r.createTime) }}</text>
            <view class="pc-td w-act">
              <text class="pc-link" @click="copy(r.txHash)">复制哈希</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <!-- 一致性校验 -->
      <view class="pc-card">
        <view class="pc-card-head">
          <text class="pc-card-title">链上链下一致性校验</text>
        </view>
        <view class="pc-card-body">
          <view class="verify-bar">
            <input
              class="pc-input"
              v-model="verifyNo"
              placeholder="输入批次编码，如 B20251105001"
              @confirm="doVerify"
            />
            <view class="pc-btn" @click="doVerify">开始校验</view>
          </view>

          <view v-if="verifyResult" class="verify-result" :class="verifyResult.passed ? 'ok' : 'bad'">
            <view class="vr-head">
              <text class="vr-title">{{ verifyResult.passed ? '校验通过' : '校验未通过' }}</text>
              <text class="vr-meta">
                {{ verifyResult.matchedRecords }}/{{ verifyResult.totalRecords }} 条一致
                · {{ formatDateTime(verifyResult.verifiedAt) }}
              </text>
            </view>
            <text class="vr-msg">{{ verifyResult.message }}</text>
          </view>

          <view v-if="verifyResult && verifyResult.items && verifyResult.items.length" class="pc-tbody">
            <view class="pc-thead inner">
              <text class="pc-th">记录</text>
              <text class="pc-th">类型</text>
              <text class="pc-th">链下哈希</text>
              <text class="pc-th">链上哈希</text>
              <text class="pc-th">回执</text>
              <text class="pc-th">结论</text>
            </view>
            <view v-for="(it, i) in verifyResult.items" :key="i" class="pc-tr">
              <text class="pc-td">{{ it.name || '-' }}</text>
              <text class="pc-td">{{ it.bizType || '-' }}</text>
              <text class="pc-td mono">{{ shortHash(it.localHash) }}</text>
              <text class="pc-td mono">{{ shortHash(it.chainHash) }}</text>
              <text class="pc-td">{{ it.receiptOk ? '正常' : '异常' }}</text>
              <view class="pc-td">
                <text class="mini-tag" :class="it.matched ? 'ok' : 'bad'">
                  {{ it.matched ? '一致' : '不一致' }}
                </text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 失败明细 -->
      <pc-modal v-model="failedVisible" title="上链失败明细" width="720px" hide-footer>
        <view v-if="!failedList.length" class="pc-none">暂无失败记录</view>
        <view v-else class="pc-tbody">
          <view class="pc-thead inner">
            <text class="pc-th">批次编码</text>
            <text class="pc-th">事件</text>
            <text class="pc-th">交易哈希</text>
            <text class="pc-th f2">失败原因</text>
            <text class="pc-th">时间</text>
          </view>
          <view v-for="f in failedList" :key="f.id" class="pc-tr">
            <text class="pc-td mono">{{ f.batchNo || '-' }}</text>
            <text class="pc-td">{{ eventName(f.eventType) }}</text>
            <text class="pc-td mono">{{ shortHash(f.txHash) }}</text>
            <view class="pc-td f2 err">{{ f.errorMsg || '-' }}</view>
            <text class="pc-td">{{ formatDateTime(f.createTime) }}</text>
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import { chainPage, chainVerifyByNo, chainStatus, chainFailed, chainRepair } from '@/api'
import { EVENT_TYPE } from '@/config/constants'
import { toast } from '@/utils/ui'
import { formatDateTime, shortHash } from '@/utils/format'
import { useUserStore } from '@/store/user'

const store = useUserStore()
/** 补登记是高危写操作，仅监管方与管理员可见（后端同样有 @RequireRoles 兜底） */
const canRepair = computed(() => store.hasPerm('chain:repair'))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const chain = ref({ enabled: false, ready: false, message: '', contractAddress: '', blockNumber: null })
const failedList = ref([])
const failedVisible = ref(false)

const query = reactive({ page: 1, size: 10, batchNo: '', status: '' })

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'SUCCESS', label: '已上链' },
  { value: 'FAILED', label: '上链失败' },
  { value: 'SKIPPED', label: '演示跳过' }
]

/**
 * 汇总口径是整张 chain_record 表（后端 /chain/status 返回 chainStat）。
 * 早先按 list.value（当前页 10 条）统计，会出现"上链成功 10"与下方"共 208 条"打架，
 * 而且翻页时这个数字还会变——统计不能依赖分页数据。
 */
const stat = computed(() => {
  const s = chain.value && chain.value.chainStat
  if (s) {
    return {
      success: s.success,
      failed: s.failed,
      skipped: s.skipped,
      rate: s.successRate,
      ready: true
    }
  }
  return { success: '—', failed: '—', skipped: '—', rate: '—', ready: false }
})

function eventName(t) {
  return EVENT_TYPE[t] || t || '-'
}

async function loadChain() {
  try {
    const res = await chainStatus()
    chain.value = res.data || chain.value
  } catch (e) {
    chain.value = { ...chain.value, enabled: false, message: '链服务状态获取失败' }
  }
}

async function loadFailed() {
  try {
    failedList.value = (await chainFailed()).data || []
  } catch (e) {
    failedList.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.batchNo) params.batchNo = query.batchNo.trim()
    if (query.status) params.status = query.status
    const res = await chainPage(params)
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

function loadAll() {
  loadChain()
  load()
  loadFailed()
}

/**
 * 补登记：把历史"未上链 / 上链失败"的批次重新注册到合约。
 * 后端已有 /chain/repair/batches，界面此前没有入口，chain:repair 成了空授权。
 */
async function doRepair() {
  const ok = await new Promise((resolve) => {
    uni.showModal({
      title: '补登记未上链批次',
      content: '将把当前所有未上链的批次重新注册到 FISCO-BCOS，过程会逐笔上链，可能需要几十秒。确认继续？',
      confirmText: '开始补登记',
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false)
    })
  })
  if (!ok) return

  uni.showLoading({ title: '上链中…', mask: true })
  try {
    const res = await chainRepair()
    const d = res.data || {}
    uni.hideLoading()
    uni.showModal({
      title: '补登记完成',
      content: `共 ${d.total || 0} 个待处理批次：本次补登记 ${d.repaired || 0} 个，此前已在链上 ${d.alreadyOnChain || 0} 个，失败 ${d.failed || 0} 个`,
      showCancel: false
    })
    loadAll()
  } catch (e) {
    uni.hideLoading()
    /* 请求层已提示 */
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.batchNo = ''
  query.status = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

function openFailed() {
  failedVisible.value = true
  if (!failedList.value.length) loadFailed()
}

function copy(text) {
  if (!text) return
  uni.setClipboardData({
    data: String(text),
    success: () => uni.showToast({ title: '已复制', icon: 'none' })
  })
}

/* ---------------- 校验 ---------------- */
const verifyNo = ref('')
const verifyResult = ref(null)

async function doVerify() {
  const no = verifyNo.value.trim()
  if (!no) return toast('请输入批次编码')
  uni.showLoading({ title: '校验中…' })
  try {
    const res = await chainVerifyByNo(no)
    verifyResult.value = res.data || null
    uni.hideLoading()
  } catch (e) {
    uni.hideLoading()
    verifyResult.value = null
    toast(e && e.message ? e.message : '校验失败')
  }
}

onShow(loadAll)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.chain-cards {
  display: flex;
  flex-direction: row;
  gap: 14px;
  margin-bottom: 16px;
}
.cc {
  flex: 1;
  background: #fff;
  border: 1px solid #e9edf2;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.cc-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.cc-label {
  font-size: 11.5px;
  color: #94a3b8;
}
.cc-badge {
  font-size: 10px;
  border-radius: 3px;
  padding: 1px 6px;
}
.cc-badge.on {
  color: #16a34a;
  background: #e8f7ee;
}
.cc-badge.off {
  color: #b45309;
  background: #fef6e0;
}
.cc-val {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-val.small {
  font-size: 11px;
  font-weight: 500;
  color: #4b5563;
}
.cc-val.green {
  color: #16a34a;
}
.cc-val.red {
  color: #d93026;
}
.slash {
  color: #cbd5e1;
  font-weight: 400;
  margin: 0 2px;
}
.orange {
  color: #c2410c;
  font-size: 15px;
}
.cc-hint {
  font-size: 10.5px;
  color: #b6bec9;
  margin-top: 3px;
}
.chain-tip {
  font-size: 11.5px;
  color: #b45309;
  background: #fef6e0;
  border: 1px solid #f7e3b5;
  border-radius: 8px;
  padding: 9px 12px;
  margin-bottom: 16px;
  line-height: 1.6;
}
.w-batch { flex: 1.5; }
.w-hash { flex: 1.4; }
.w-status { flex: 1; }
.w-time { flex: 1.3; }
.w-act { flex: 0.9; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11px;
}
.verify-bar {
  display: flex;
  flex-direction: row;
  gap: 10px;
  margin-bottom: 14px;
}
.verify-result {
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.verify-result.ok {
  background: #e8f7ee;
  border: 1px solid #bfe6d0;
}
.verify-result.bad {
  background: #fdeceb;
  border: 1px solid #f7c9c5;
}
.vr-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.vr-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}
.vr-meta {
  font-size: 11px;
  color: #64748b;
}
.vr-msg {
  display: block;
  font-size: 11.5px;
  color: #4b5563;
  margin-top: 6px;
  line-height: 1.6;
}
.pc-thead.inner .pc-th {
  flex: 1;
}
.pc-thead.inner .pc-th.f2,
.pc-tr .pc-td.f2 {
  flex: 2.2;
}
.err {
  color: #d93026;
}
.mini-tag {
  font-size: 10px;
  border-radius: 3px;
  padding: 1px 6px;
}
.mini-tag.ok {
  color: #16a34a;
  background: #e8f7ee;
}
.mini-tag.bad {
  color: #d93026;
  background: #fdeceb;
}
</style>
