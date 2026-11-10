<template>
  <pc-shell title="销售管理">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">销售管理</text>
          <text class="pc-sub">销售流向记录上链，实现「从田间到餐桌」的闭环追溯</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增销售记录</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <pc-select
            v-model="query.batchId"
            :options="batchOpts"
            placeholder="全部批次"
            width="220px"
            @change="search"
          />
          <input
            class="pc-input"
            v-model="query.batchNo"
            placeholder="按批次编码精确查询"
            @confirm="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-date">销售日期</text>
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th">销售市场</text>
          <text class="pc-th">销售商</text>
          <text class="pc-th">单价</text>
          <text class="pc-th">数量</text>
          <text class="pc-th">金额</text>
          <text class="pc-th">买家</text>
          <text class="pc-th w-chain">上链</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无销售记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-date">{{ r.salesDate || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td w-market">{{ r.marketName || '-' }}</text>
            <text class="pc-td w-seller">{{ r.sellerName || '-' }}</text>
            <text class="pc-td">￥{{ r.price || '-' }}</text>
            <text class="pc-td">{{ fmtQty(r.quantity, r.unit) }}</text>
            <text class="pc-td">{{ amount(r) }}</text>
            <text class="pc-td">{{ r.buyerName || '-' }}</text>
            <view class="pc-td w-chain"><pc-status :value="r.chainStatus" type="chain" /></view>
            <view class="pc-td w-act">
              <text v-if="canAuditRow(r)" class="pc-link" @click="openAudit(r)">审核</text>
              <text v-else class="muted">-</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal
        v-model="createVisible"
        title="新增销售记录"
        width="600px"
        ok-text="登记并上链"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">所属批次<text class="req">*</text></text>
              <pc-select
                v-model="form.batchId"
                :options="batchPickOpts"
                placeholder="请选择批次"
                width="100%"
                pop-width="360px"
                empty-hint="暂无可销售的批次
批次完成物流交接后，才会进入这里供登记销售"
              />
            </view>
            <view class="pc-field">
              <text class="pc-label">销售日期<text class="req">*</text></text>
              <picker mode="date" :value="form.salesDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.salesDate }">{{ form.salesDate || '请选择日期' }}</text>
                </view>
              </picker>
            </view>
            <view class="pc-field">
              <text class="pc-label">销售市场</text>
              <input class="pc-input" v-model="form.marketName" placeholder="如：XX 农产品批发市场" />
            </view>
            <view class="pc-field">
              <text class="pc-label">单价（元）<text class="req">*</text></text>
              <input class="pc-input" v-model="form.price" type="digit" placeholder="0.00" />
            </view>
            <view class="pc-field">
              <text class="pc-label">销售数量<text class="req">*</text></text>
              <input class="pc-input" v-model="form.quantity" type="digit" placeholder="0.00" />
            </view>
            <view class="pc-field">
              <text class="pc-label">计量单位</text>
              <pc-select v-model="form.unit" :options="unitOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">买家名称</text>
              <input class="pc-input" v-model="form.buyerName" placeholder="买家或采购单位" />
            </view>
            <view class="pc-field">
              <text class="pc-label">买家电话</text>
              <input class="pc-input" v-model="form.buyerPhone" placeholder="联系电话" />
            </view>
          </view>
          <text class="pc-hint">销售登记后批次状态推进为「销售中」；异常批次会被后端强制拦截。</text>
            <view class="pc-field full">
              <text class="pc-label">现场实证（图片 / 视频）</text>
              <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
            </view>
        </view>
      </pc-modal>

      <!-- 审核 -->
      <pc-modal v-model="auditVisible" title="销售记录审核" width="460px" @ok="submitAudit">
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
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import { salesPage, createSales, auditSales, batchOptions } from '@/api'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'
import { toBatchOption } from '@/utils/batch-label'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const batches = ref([])
/** 新增弹窗用：可销售的批次 */
const creatableBatches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('sales:create'))

function canAuditRow(r) {
  return (
    store.hasPerm('sales:audit') &&
    r.auditStatus === 'PENDING' &&
    ['VILLAGE_ADMIN', 'REGULATOR', 'ADMIN'].includes(store.roleCode)
  )
}

/** 查询下拉：全部批次 */
const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

/** 新增下拉：两行 + 状态 */
const batchPickOpts = computed(() => creatableBatches.value.map(toBatchOption))

const unitOptions = ['kg', '吨', '斤', '箱', '件'].map((u) => ({ value: u, label: u }))

function fmtQty(v, unit) {
  return formatQty(v, unit || '')
}

function amount(r) {
  const p = Number(r.price)
  const q = Number(r.quantity)
  if (!Number.isFinite(p) || !Number.isFinite(q)) return '-'
  return `￥${(p * q).toFixed(2)}`
}

/** 查询下拉：全部批次 */
async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions()).data || []
  } catch (e) {
    batches.value = []
  }
}

/**
 * 新增下拉：运输中 + 销售中，且已核验、无异常。
 * 原先前端按 QUALIFIED/IN_TRANSIT/ON_SALE 过滤，但后端只认 IN_TRANSIT/ON_SALE，
 * 于是"已质检合格"的批次能选、点了却被拒——过滤口径必须与后端一致。
 */
async function loadCreatableBatches() {
  try {
    creatableBatches.value =
      (await batchOptions({ status: 'IN_TRANSIT,ON_SALE', usable: true })).data || []
  } catch (e) {
    creatableBatches.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.batchId) params.batchId = query.batchId
    if (query.batchNo) params.batchNo = query.batchNo.trim()
    const res = await salesPage(params)
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
  query.batchId = ''
  query.batchNo = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onDate(e) {
  form.salesDate = e.detail.value
}

const createVisible = ref(false)
const submitting = ref(false)
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  salesDate: today(),
  marketName: '',
  price: '',
  quantity: '',
  unit: 'kg',
  buyerName: '',
  buyerPhone: ''
})

async function openCreate() {
  await loadCreatableBatches()
  form.batchId = ''
  form.salesDate = today()
  form.marketName = ''
  form.price = ''
  form.quantity = ''
  form.unit = 'kg'
  form.buyerName = ''
  form.buyerPhone = ''
  createVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')
  if (!form.salesDate) return toast('请选择销售日期')
  if (!form.price || Number(form.price) <= 0) return toast('请填写有效的单价')
  if (!form.quantity || Number(form.quantity) <= 0) return toast('请填写有效的销售数量')

  submitting.value = true
  try {
    await createSales({
      batchId: form.batchId,
      marketName: form.marketName,
      salesDate: form.salesDate,
      price: Number(form.price),
      quantity: Number(form.quantity),
      unit: form.unit,
      buyerName: form.buyerName,
      buyerPhone: form.buyerPhone,
      evidenceIds: uploader.value ? uploader.value.ids() : []
    })
    uni.showToast({ title: '销售已登记', icon: 'success' })
    createVisible.value = false
    cache.invalidate('batch', 'sales')
    query.page = 1
    load()
  } catch (e) {
    /* 已提示 */
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

function openAudit(r) {
  auditForm.id = r.id
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
    await auditSales(auditForm.id, {
      auditStatus: auditForm.auditStatus,
      auditRemark: auditForm.auditRemark
    })
    uni.showToast({ title: '审核完成', icon: 'success' })
    auditVisible.value = false
    cache.invalidate('sales')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

onShow(() => {
  load()
  loadBatches()
})
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-date { flex: 1.1; }
.w-batch { flex: 1.5; }
/* 门店名与销售商都是完整名称，默认宽度会把"永辉超市闽侯旗舰店"截成"永辉超市闽侯…" */
.w-market { flex: 1.4; }
.w-seller { flex: 1.32; }
.w-chain { flex: 1; }
.w-act { flex: 0.7; }
.pc-tr .w-act,
.pc-td.w-act {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.muted {
  color: #c3ccd6;
  font-size: 12px;
}
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
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
</style>
