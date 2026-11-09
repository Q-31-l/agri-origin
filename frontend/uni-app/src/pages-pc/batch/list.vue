<template>
  <pc-shell title="批次管理">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">批次管理</text>
          <text class="pc-sub">覆盖种植、采收、检测、运输、销售全流程的批次台账</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新建批次</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.keyword"
            placeholder="搜索批次编码 / 产品 / 溯源码"
            @confirm="search"
          />
          <pc-select
            v-model="query.status"
            :options="statusOptions"
            placeholder="全部状态"
            width="140px"
            @change="search"
          />
          <pc-select
            v-model="query.abnormalFlag"
            :options="abnormalOptions"
            placeholder="异常筛选"
            width="130px"
            @change="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-no">批次编码</text>
          <text class="pc-th w-prod">产品</text>
          <text class="pc-th w-farmer">农户 / 农场</text>
          <text class="pc-th w-village">所属村</text>
          <text class="pc-th w-date">种植日期</text>
          <text class="pc-th w-qty">数量</text>
          <text class="pc-th w-status">状态</text>
          <text class="pc-th w-chain">上链</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">没有符合条件的批次</view>
        <view v-else class="pc-tbody">
          <view v-for="b in list" :key="b.id" class="pc-tr">
            <view class="pc-td w-no">
              <text class="pc-link mono" @click="toDetail(b)">{{ b.batchNo }}</text>
              <text v-if="b.abnormalFlag === 1" class="abn-flag">异常</text>
            </view>
            <view class="pc-td w-prod strong">
              <text class="cell-main">{{ b.productName || '-' }}</text>
              <text class="sub">{{ b.variety || '' }}</text>
            </view>
            <view class="pc-td w-farmer">
              <text class="cell-main">{{ b.farmerName || '-' }}</text>
              <text class="sub">{{ b.farmName || '' }}</text>
            </view>
            <text class="pc-td w-village">{{ b.villageName || '-' }}</text>
            <text class="pc-td w-date">{{ b.plantingDate || '-' }}</text>
            <text class="pc-td w-qty">{{ fmtQty(b.quantity, b.unit) }}</text>
            <view class="pc-td w-status">
              <pc-status :value="b.status" type="batch" />
              <text v-if="b.pendingStage" class="pending-chip">待审 · {{ b.pendingStage }}</text>
            </view>
            <view class="pc-td w-chain">
              <pc-status :value="b.chainStatus" type="chain" />
            </view>
            <view class="pc-td w-act">
              <text class="pc-link" @click="toDetail(b)">详情</text>
              <text v-if="canVerify" class="pc-link" @click="onVerify(b)">核验</text>
              <text v-if="canAudit(b)" class="pc-link" @click="openAudit(b)">审核</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <!-- 新建批次 -->
      <pc-modal
        v-model="createVisible"
        title="新建批次"
        width="600px"
        ok-text="提交并上链"
        @ok="submitCreate"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">产品<text class="req">*</text></text>
              <view class="pc-product-row">
                <input
                  class="pc-input"
                  v-model="form.productName"
                  placeholder="手写产品名，如：草莓 / 玉米 / 高粱"
                  @input="onProductInput"
                />
                <pc-select
                  v-model="form.productId"
                  :options="productOptions"
                  placeholder="或选品类"
                  width="120px"
                  @change="onProductPick"
                />
              </view>
            </view>
            <view class="pc-field">
              <text class="pc-label">所属农场</text>
              <pc-select
                v-model="form.farmId"
                :options="farmOptions"
                placeholder="请选择农场"
                width="100%"
              />
            </view>
            <view class="pc-field">
              <text class="pc-label">种植日期<text class="req">*</text></text>
              <picker mode="date" :value="form.plantingDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.plantingDate }">
                    {{ form.plantingDate || '请选择日期' }}
                  </text>
                </view>
              </picker>
            </view>
            <view class="pc-field">
              <text class="pc-label">预计产量<text class="req">*</text></text>
              <input class="pc-input" v-model="form.quantity" type="digit" placeholder="0.00" />
            </view>
            <view class="pc-field">
              <text class="pc-label">计量单位</text>
              <pc-select v-model="form.unit" :options="unitOptions" width="100%" />
            </view>
            <view class="pc-field full">
              <text class="pc-label">备注</text>
              <textarea
                class="pc-textarea"
                v-model="form.remark"
                placeholder="可填写种植方式、地块编号等信息"
                maxlength="200"></textarea>
            </view>
          </view>
          <text class="pc-hint">创建后系统自动生成批次编码与溯源码，并将关键信息写入联盟链存证。</text>
        </view>
      </pc-modal>

      <!-- 审核 -->
      <pc-modal v-model="auditVisible" title="批次核验审核" width="460px" @ok="submitAudit">
        <view class="pc-form">
          <view class="pc-field full">
            <text class="pc-label">审核结论</text>
            <pc-select v-model="auditForm.auditStatus" :options="auditOptions" width="100%" />
          </view>
          <view class="pc-field full">
            <text class="pc-label">审核意见</text>
            <textarea
              class="pc-textarea"
              v-model="auditForm.auditRemark"
              placeholder="填写审核意见，驳回时必填"
              maxlength="200"></textarea>
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useRealtime } from '@/composables/useRealtime'
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import {
  batchPage,
  verifyBatch,
  auditBatch,
  createBatch,
  productList,
  farmList
} from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { BATCH_STATUS } from '@/config/constants'
import { ROUTES } from '@/config/routes'
import { buildUrl } from '@/utils/router'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  abnormalFlag: ''
})

const statusOptions = [
  { value: '', label: '全部状态' },
  ...Object.keys(BATCH_STATUS).map((k) => ({ value: k, label: BATCH_STATUS[k].text }))
]

const abnormalOptions = [
  { value: '', label: '不筛选' },
  { value: 1, label: '仅异常批次' },
  { value: 0, label: '仅正常批次' }
]

const canCreate = computed(() => store.hasPerm('batch:create'))
const canVerify = computed(() => store.hasPerm('chain:verify'))

const unitOptions = ['kg', '吨', '斤', '箱', '件'].map((u) => ({ value: u, label: u }))

/* ---------------- 列表 ---------------- */
function fmtQty(v, unit) {
  return formatQty(v, unit || '')
}

function canAudit(b) {
  if (!store.hasPerm('batch:audit')) return false
  if (b.auditStatus !== 'PENDING') return false
  return ['VILLAGE_ADMIN', 'REGULATOR', 'ADMIN'].includes(store.roleCode)
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword.trim()
    if (query.status) params.status = query.status
    if (query.abnormalFlag !== '') params.abnormalFlag = query.abnormalFlag
    const res = await batchPage(params)
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
  query.keyword = ''
  query.status = ''
  query.abnormalFlag = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

function toDetail(b) {
  uni.navigateTo({ url: buildUrl(ROUTES.PC_BATCH_DETAIL, { id: b.id }) })
}

/* ---------------- 新建 ---------------- */
const createVisible = ref(false)
const submitting = ref(false)
const products = ref([])
const farms = ref([])

const form = reactive({
  productId: '',
  productName: '',
  farmId: '',
  plantingDate: '',
  quantity: '',
  unit: 'kg',
  remark: ''
})

const productOptions = computed(() =>
  products.value.map((p) => ({ value: p.id, label: p.productName }))
)
const farmOptions = computed(() =>
  farms.value.map((f) => ({ value: f.id, label: f.farmName }))
)

function onDate(e) {
  form.plantingDate = e.detail.value
}

async function openCreate() {
  createVisible.value = true
  form.productId = ''
  form.productName = ''
  form.farmId = ''
  form.plantingDate = ''
  form.quantity = ''
  form.unit = 'kg'
  form.remark = ''
  try {
    if (!products.value.length) products.value = (await productList()).data || []
    if (!farms.value.length) farms.value = (await farmList()).data || []
  } catch (e) {
    /* 权限不足时静默，用户可手动填写 */
  }
}

/** 手写产品名后清掉选中的品类 id，走"手写建批次" */
function onProductInput() {
  form.productId = ''
}

/** 从品类下拉选中后，把产品名同步到手写框，保持一致 */
function onProductPick(v) {
  const p = products.value.find((x) => String(x.id) === String(v))
  if (p) form.productName = p.productName
}

async function submitCreate() {
  if (submitting.value) return
  if (!form.productId && !form.productName.trim()) return toast('请填写或选择产品')
  if (!form.plantingDate) return toast('请选择种植日期')
  if (!form.quantity || Number(form.quantity) <= 0) return toast('请填写有效的预计产量')

  submitting.value = true
  try {
    const payload = {
      productId: form.productId || null,
      productName: form.productName.trim(),
      plantingDate: form.plantingDate,
      quantity: Number(form.quantity),
      unit: form.unit,
      remark: form.remark
    }
    if (form.farmId) payload.farmId = form.farmId
    await createBatch(payload)
    uni.showToast({ title: '创建成功', icon: 'success' })
    createVisible.value = false
    cache.invalidate('batch')
    query.page = 1
    load()
  } catch (e) {
    /* request 层已提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------------- 审核 ---------------- */
const auditVisible = ref(false)
const auditOptions = [
  { value: 'APPROVED', label: '审核通过' },
  { value: 'REJECTED', label: '驳回' }
]
const auditForm = reactive({ id: null, auditStatus: 'APPROVED', auditRemark: '' })

function openAudit(b) {
  auditForm.id = b.id
  auditForm.auditStatus = 'APPROVED'
  auditForm.auditRemark = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditForm.id) return
  if (auditForm.auditStatus === 'REJECTED' && !auditForm.auditRemark.trim()) {
    return toast('驳回时必须填写审核意见')
  }
  try {
    await auditBatch(auditForm.id, {
      auditStatus: auditForm.auditStatus,
      auditRemark: auditForm.auditRemark
    })
    uni.showToast({ title: '审核完成', icon: 'success' })
    auditVisible.value = false
    cache.invalidate('batch')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

/* ---------------- 核验 ---------------- */
async function onVerify(b) {
  uni.showLoading({ title: '核验中…' })
  try {
    const res = await verifyBatch(b.id)
    const d = res.data || {}
    uni.hideLoading()
    uni.showModal({
      title: d.passed ? '核验通过' : '核验未通过',
      content: d.message || (d.passed ? '链上链下数据完全一致' : '存在不一致记录，请核查'),
      showCancel: false
    })
  } catch (e) {
    uni.hideLoading()
    toast(e && e.message ? e.message : '核验失败')
  }
}

onShow(() => {
  if (cache.isDirty('batch')) {
    cache.markFresh('batch')
    query.page = 1
  }
  load()
})

// 其他端写入批次 / 处置异常后自动刷新（弹窗打开时跳过，避免打断录入）
useRealtime(() => {
  if (createVisible.value || auditVisible.value || submitting.value) return
  load()
}, { types: ['batch', 'abnormal'] })
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-no { flex: 1.7; }
.w-prod { flex: 1.4; }
.w-farmer { flex: 1.5; }
.w-village { flex: 1; }
.w-date { flex: 1.05; }
.w-qty { flex: 0.85; }
.w-status { flex: 1.25; }
.w-chain { flex: 1.1; }
.w-act { flex: 1.15; }
.pending-chip {
  display: inline-block;
  margin-top: 3px;
  font-size: 10.5px;
  color: #c2410c;
  background: #fff1e6;
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
}

.pc-tr .w-act,
.pc-td.w-act {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
.cell-main {
  display: block;
  font-size: 12px;
}
.sub {
  display: block;
  font-size: 10.5px;
  color: #a3aeb9;
  font-weight: 400;
  line-height: 1.4;
}
.abn-flag {
  display: inline-block;
  margin-left: 6px;
  font-size: 9.5px;
  line-height: 16px;
  color: #c0392b;
  background: #fdeceb;
  border: 1px solid #f6d5d0;
  border-radius: 4px;
  padding: 0 5px;
  white-space: nowrap;
  vertical-align: middle;
}
.picker-box {
  display: flex;
  align-items: center;
  box-sizing: border-box;
}
.picker-box .ph {
  color: #b6bec9;
  font-size: 12px;
}
/* 产品行：左侧手写输入框 + 右侧品类下拉 */
.pc-product-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.pc-product-row .pc-input {
  flex: 1;
  min-width: 0;
}
</style>
