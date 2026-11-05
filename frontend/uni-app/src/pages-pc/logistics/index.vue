<template>
  <pc-shell title="物流运输">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">物流运输</text>
          <text class="pc-sub">发运交接与温控记录，异常批次将被强制熔断禁止发运</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增发运记录</view>
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
          <text class="pc-th w-no">物流单号</text>
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th">发货方</text>
          <text class="pc-th">收货方</text>
          <text class="pc-th w-car">车牌 / 司机</text>
          <text class="pc-th w-time">发运时间</text>
          <text class="pc-th">温控</text>
          <text class="pc-th">状态</text>
          <text class="pc-th w-chain">上链</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无物流记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-no mono">{{ r.logisticsNo || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td">{{ r.fromOrg || '-' }}</text>
            <text class="pc-td">{{ r.toOrg || '-' }}</text>
            <view class="pc-td w-car">
              <text class="cell-main">{{ r.vehicleNo || '-' }}</text>
              <text class="sub">{{ r.driverName || '' }} {{ r.driverPhone || '' }}</text>
            </view>
            <text class="pc-td w-time">{{ formatDateTime(r.departTime) }}</text>
            <text class="pc-td">{{ r.temperature || '-' }}</text>
            <text class="pc-td">{{ statusText(r.status) }}</text>
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
        title="新增发运记录"
        width="620px"
        ok-text="登记并上链"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">所属批次<text class="req">*</text></text>
              <pc-select v-model="form.batchId" :options="batchOpts" placeholder="请选择批次" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">物流单号</text>
              <input class="pc-input" v-model="form.logisticsNo" placeholder="不填则自动生成" />
            </view>
            <view class="pc-field">
              <text class="pc-label">发货方</text>
              <input class="pc-input" v-model="form.fromOrg" placeholder="如：XX 合作社" />
            </view>
            <view class="pc-field">
              <text class="pc-label">收货方</text>
              <input class="pc-input" v-model="form.toOrg" placeholder="如：XX 商超" />
            </view>
            <view class="pc-field">
              <text class="pc-label">车牌号</text>
              <input class="pc-input" v-model="form.vehicleNo" placeholder="如：粤A12345" />
            </view>
            <view class="pc-field">
              <text class="pc-label">司机姓名</text>
              <input class="pc-input" v-model="form.driverName" placeholder="司机姓名" />
            </view>
            <view class="pc-field">
              <text class="pc-label">司机电话</text>
              <input class="pc-input" v-model="form.driverPhone" placeholder="联系电话" />
            </view>
            <view class="pc-field">
              <text class="pc-label">温控要求</text>
              <input class="pc-input" v-model="form.temperature" placeholder="如：0-4℃" />
            </view>
            <view class="pc-field">
              <text class="pc-label">发运日期</text>
              <picker mode="date" :value="form.departDate" @change="(e) => (form.departDate = e.detail.value)">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.departDate }">{{ form.departDate || '请选择日期' }}</text>
                </view>
              </picker>
            </view>
            <view class="pc-field">
              <text class="pc-label">发运时间</text>
              <picker mode="time" :value="form.departTime" @change="(e) => (form.departTime = e.detail.value)">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.departTime }">{{ form.departTime || '请选择时间' }}</text>
                </view>
              </picker>
            </view>
            <view class="pc-field full">
              <text class="pc-label">备注</text>
              <textarea class="pc-textarea" v-model="form.remark" placeholder="可填写运输路线、交接说明" maxlength="200"></textarea>
            </view>
          </view>
          <text class="pc-hint">发运登记后批次状态推进为「运输中」；若批次处于异常管控，后端将拒绝写入。</text>
            <view class="pc-field full">
              <text class="pc-label">现场实证（图片 / 视频）</text>
              <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
            </view>
        </view>
      </pc-modal>

      <!-- 审核 -->
      <pc-modal v-model="auditVisible" title="物流记录审核" width="460px" @ok="submitAudit">
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
import { logisticsPage, createLogistics, auditLogistics, batchOptions } from '@/api'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { formatDateTime } from '@/utils/format'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const batches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('logistics:create'))

function canAuditRow(r) {
  return (
    store.hasPerm('logistics:audit') &&
    r.auditStatus === 'PENDING' &&
    ['VILLAGE_ADMIN', 'REGULATOR', 'ADMIN'].includes(store.roleCode)
  )
}

const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

const STATUS_TEXT = {
  DEPARTED: '运输中',
  ARRIVED: '已送达',
  HANDOVER: '已交接'
}

function statusText(s) {
  return STATUS_TEXT[s] || s || '-'
}

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions({ status: 'QUALIFIED' })).data || []
  } catch (e) {
    batches.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.batchId) params.batchId = query.batchId
    if (query.batchNo) params.batchNo = query.batchNo.trim()
    const res = await logisticsPage(params)
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

function nowTime() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

const createVisible = ref(false)
const submitting = ref(false)
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  logisticsNo: '',
  fromOrg: '',
  toOrg: '',
  vehicleNo: '',
  driverName: '',
  driverPhone: '',
  temperature: '',
  departDate: today(),
  departTime: nowTime(),
  remark: ''
})

async function openCreate() {
  await loadBatches()
  form.batchId = ''
  form.logisticsNo = ''
  form.fromOrg = ''
  form.toOrg = ''
  form.vehicleNo = ''
  form.driverName = ''
  form.driverPhone = ''
  form.temperature = ''
  form.departDate = today()
  form.departTime = nowTime()
  form.remark = ''
  createVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')

  const departTime =
    form.departDate && form.departTime
      ? `${form.departDate} ${form.departTime}:00`
      : form.departDate || ''

  submitting.value = true
  try {
    await createLogistics({
      batchId: form.batchId,
      logisticsNo: form.logisticsNo,
      fromOrg: form.fromOrg,
      toOrg: form.toOrg,
      vehicleNo: form.vehicleNo,
      driverName: form.driverName,
      driverPhone: form.driverPhone,
      temperature: form.temperature,
      departTime,
      status: 'DEPARTED',
      remark: form.remark,
      evidenceIds: uploader.value ? uploader.value.ids() : []
    })
    uni.showToast({ title: '发运已登记', icon: 'success' })
    createVisible.value = false
    cache.invalidate('batch', 'logistics')
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
    await auditLogistics(auditForm.id, {
      auditStatus: auditForm.auditStatus,
      auditRemark: auditForm.auditRemark
    })
    uni.showToast({ title: '审核完成', icon: 'success' })
    auditVisible.value = false
    cache.invalidate('logistics')
    load()
  } catch (e) {
    /* 已提示 */
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-no { flex: 1.4; }
.w-batch { flex: 1.5; }
.w-car { flex: 1.25; }
.w-time { flex: 1.6; }
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
.cell-main {
  display: block;
  font-size: 12px;
}
.sub {
  display: block;
  font-size: 10.5px;
  color: #a3aeb9;
  line-height: 1.4;
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
