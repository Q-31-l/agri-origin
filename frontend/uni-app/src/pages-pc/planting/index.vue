<template>
  <pc-shell title="农事记录">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">农事记录</text>
          <text class="pc-sub">种植、施肥、用药、灌溉等操作记录的链上存证台账</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增农事记录</view>
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
          <text class="pc-th w-date">操作日期</text>
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th w-type">操作类型</text>
          <text class="pc-th w-content">操作内容</text>
          <text class="pc-th w-material">投入品</text>
          <text class="pc-th w-dosage">用量</text>
          <text class="pc-th w-op">操作人</text>
          <text class="pc-th w-audit">审核</text>
          <text class="pc-th w-chain">上链</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无农事记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-date">{{ r.operationDate || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td w-type">{{ plantingTypeLabel(r.recordType) }}</text>
            <view class="pc-td w-content wrap">{{ r.content || '-' }}</view>
            <text class="pc-td w-material">{{ r.materialName || '-' }}</text>
            <text class="pc-td w-dosage">{{ r.dosage || '-' }}</text>
            <text class="pc-td w-op">{{ r.operatorName || '-' }}</text>
            <view class="pc-td w-audit">
              <pc-status :value="r.auditStatus" type="audit" />
              <text v-if="canAudit && r.auditStatus === 'PENDING'" class="au-link" @click="doAudit(r)">去审核</text>
            </view>
            <view class="pc-td w-chain"><pc-status :value="r.chainStatus" type="chain" /></view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal
        v-model="createVisible"
        title="新增农事记录"
        width="600px"
        ok-text="提交并上链"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">所属批次</text>
              <pc-select
                v-model="form.batchId"
                :options="batchPickOpts"
                placeholder="可留空，直接手写作物"
                width="100%"
                pop-width="360px"
                empty-hint="暂无已核验的批次
可不选批次，在下方手写作物名提交，村管审核通过后自动建档批次"
              />
            </view>
            <view v-if="!form.batchId" class="pc-field">
              <text class="pc-label">作物<text class="req">*</text></text>
              <input class="pc-input" v-model="form.productName" placeholder="手写作物名，如：草莓 / 玉米 / 高粱" />
            </view>
            <view class="pc-field">
              <text class="pc-label">操作类型</text>
              <pc-select v-model="form.recordType" :options="typeOptions" width="100%" />
            </view>
            <view v-if="selectedBatch" class="pc-field full">
              <view class="binfo">
                <text class="binfo-t">{{ batchDesc(selectedBatch) || '未关联农场' }}</text>
                <pc-status :value="selectedBatch.status" type="batch" />
                <pc-status :value="selectedBatch.auditStatus" type="audit" />
              </view>
            </view>
            <view class="pc-field">
              <text class="pc-label">操作日期<text class="req">*</text></text>
              <picker mode="date" :value="form.operationDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.operationDate }">{{ form.operationDate || '请选择日期' }}</text>
                </view>
              </picker>
            </view>
            <view class="pc-field">
              <text class="pc-label">投入品名称</text>
              <input class="pc-input" v-model="form.materialName" placeholder="如：有机肥 / 生物农药" />
            </view>
            <view class="pc-field">
              <text class="pc-label">用量</text>
              <input class="pc-input" v-model="form.dosage" placeholder="如：20kg/亩" />
            </view>
            <view class="pc-field">
              <text class="pc-label">作业地点</text>
              <view class="loc">
                <view class="loc-main">
                  <text class="loc-t" :class="{ ph: !form.location }">{{ form.location || '待定位' }}</text>
                  <text class="loc-note">{{ locNote }}</text>
                </view>
                <text class="loc-r" @click="autoLocate">{{ locating ? '定位中…' : '重测' }}</text>
              </view>
            </view>
            <view class="pc-field full">
              <text class="pc-label">操作内容<text class="req">*</text></text>
              <textarea
                class="pc-textarea"
                v-model="form.content"
                placeholder="描述本次农事操作的具体内容"
                maxlength="300"></textarea>
            </view>
            <view class="pc-field full">
              <text class="pc-label">现场实证（图片 / 视频）<text class="req">*</text></text>
              <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
              <text class="pc-hint">至少上传 1 个图片或视频；证据哈希会随本次记录一并上链。</text>
            </view>
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import { plantingPage, createPlanting, auditPlanting, batchOptions } from '@/api'
import { showSubmitNotice } from '@/utils/submit-notice'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { PLANTING_TYPE_OPTIONS, plantingTypeLabel } from '@/config/constants'
import { batchDesc, batchPlace, toBatchOption } from '@/utils/batch-label'
import { locate, formatCoord, explainLocationError } from '@/utils/geo'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
/** 查询下拉：全部批次，方便回查历史 */
const batches = ref([])
/** 新增下拉：只放能真正录进去的（种植中 + 已核验 + 无异常） */
const creatableBatches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('planting:create'))
const canAudit = computed(() => store.hasPerm('planting:audit'))

/**
 * 村级审核：/planting/{id}/audit 早就有了，但界面一直没有入口，
 * 导致 planting:audit 成了悬空权限——村管能看到"待审核"却点不动。
 */
async function doAudit(row) {
  const pick = await new Promise((resolve) => {
    uni.showActionSheet({
      itemList: ['审核通过', '驳回'],
      success: (res) => resolve(res.tapIndex),
      fail: () => resolve(-1)
    })
  })
  if (pick < 0) return
  const approve = pick === 0

  const input = await new Promise((resolve) => {
    uni.showModal({
      title: approve ? '审核通过' : '驳回农事记录',
      editable: true,
      placeholderText: approve ? '审核意见（可选）' : '请填写驳回原因',
      success: (res) => resolve(res),
      fail: () => resolve({ cancel: true })
    })
  })
  if (!input || input.cancel) return
  const remark = (input.content || '').trim()
  if (!approve && !remark) return toast('驳回时必须填写原因')

  try {
    await auditPlanting(row.id, {
      auditStatus: approve ? 'APPROVED' : 'REJECTED',
      auditRemark: remark
    })
    toast(approve ? '已通过' : '已驳回')
    cache.invalidate('planting:list')
    load()
  } catch (e) {
    /* 请求层已提示 */
  }
}

const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

/** 新增弹窗里的批次下拉：两行 + 状态，避免只看到流水码选错批次 */
const batchPickOpts = computed(() => creatableBatches.value.map(toBatchOption))

const typeOptions = PLANTING_TYPE_OPTIONS

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions()).data || []
  } catch (e) {
    batches.value = []
  }
}

/** 每次开弹窗都重新拉：别的端刚审核通过/刚采收，批次状态随时会变 */
async function loadCreatableBatches() {
  try {
    creatableBatches.value = (await batchOptions({ status: 'PLANTING', usable: true })).data || []
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
    const res = await plantingPage(params)
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

/* ---------------- 新增 ---------------- */
const createVisible = ref(false)
const submitting = ref(false)
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  productName: '',
  recordType: 'SOIL',
  operationDate: today(),
  content: '',
  materialName: '',
  dosage: '',
  gps: '',
  location: ''
})

const selectedBatch = computed(
  () => creatableBatches.value.find((b) => String(b.id) === String(form.batchId)) || null
)

const locating = ref(false)
const locNote = ref('')

/** 作业地点 = 地块（村 + 农场）+ 自动定位坐标，人看得懂、机器也对得上 */
function composeLocation() {
  const base = batchPlace(selectedBatch.value)
  if (!form.gps) {
    form.location = base
    return
  }
  form.location = base ? `${base}（${form.gps}）` : `已定位 ${form.gps}`
}

/**
 * 打开新增就自动定位，不需要用户点。
 * 定位失败不阻塞提交：地点退回"村 + 农场"，坐标留空，并在下方说明原因。
 */
async function autoLocate() {
  if (locating.value) return
  locating.value = true
  locNote.value = '正在获取当前位置…'
  try {
    const pos = await locate({ timeout: 8000 })
    form.gps = formatCoord(pos.lat, pos.lng)
    locNote.value = `已自动定位${pos.accuracy ? `（精度约 ${pos.accuracy} 米）` : ''}`
  } catch (e) {
    form.gps = ''
    locNote.value = explainLocationError(e)
  } finally {
    locating.value = false
    composeLocation()
  }
}

// 换批次时地块会变，地点文本要跟着更新（坐标仍保留本次定位结果）
watch(() => form.batchId, composeLocation)

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onDate(e) {
  form.operationDate = e.detail.value
}

async function openCreate() {
  await loadCreatableBatches()
  form.batchId = ''
  form.productName = ''
  form.recordType = 'SOIL'
  form.operationDate = today()
  form.content = ''
  form.materialName = ''
  form.dosage = ''
  form.gps = ''
  form.location = ''
  evidenceFiles.value = []
  createVisible.value = true
  locNote.value = ''
  // 弹窗一打开就定位，用户不用点任何按钮
  autoLocate()
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId && !form.productName.trim()) return toast('请选择批次或手写作物名')
  if (!form.operationDate) return toast('请选择操作日期')
  if (!form.content.trim()) return toast('请填写操作内容')
  if (uploader.value && uploader.value.isUploading()) return toast('实证文件仍在上传，请稍候')
  const ids = uploader.value ? uploader.value.ids() : []
  if (!ids.length) return toast('请上传现场实证（图片或视频至少 1 个）')

  submitting.value = true
  try {
    const created = await createPlanting({
      batchId: form.batchId || null,
      productName: form.productName.trim(),
      recordType: form.recordType,
      operationDate: form.operationDate,
      content: form.content,
      materialName: form.materialName,
      dosage: form.dosage,
      gps: form.gps,
      location: form.location,
      evidenceIds: ids
    })
    uni.showToast({ title: '记录已提交', icon: 'success' })
    await showSubmitNotice('PLANTING', created && created.data)
    createVisible.value = false
    cache.invalidate('batch', 'planting')
    query.page = 1
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

// 查询下拉的候选批次要在进页面时就备好，不能等用户点过"新增"才加载
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
.w-date { flex: 1; }
.w-batch { flex: 1.4; }
.w-type { flex: 1; }
.w-content { flex: 2.45; }
.w-material { flex: 1.1; }
.w-dosage { flex: 0.9; }
.w-op { flex: 0.9; }
.w-audit { flex: 1.1; display: flex; flex-direction: row; align-items: center; gap: 6px; }
.au-link {
  font-size: 11.5px;
  color: #1f7a4d;
  cursor: pointer;
  white-space: nowrap;
}
.au-link:hover { text-decoration: underline; }
.w-chain { flex: 1; }
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
.binfo {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  background: #f6faf7;
  border: 1px solid #e3efe7;
  border-radius: 6px;
  padding: 7px 10px;
}
.binfo-t {
  font-size: 11.5px;
  color: #5b6b7a;
}
.loc {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  background: #fbfcfd;
  padding: 4px 10px;
  box-sizing: border-box;
}
.loc-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}
.loc-t {
  font-size: 12px;
  color: #334155;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.loc-t.ph {
  color: #b6bec9;
}
.loc-note {
  font-size: 10.5px;
  color: #93a0ad;
  margin-top: 1px;
}
.loc-r {
  font-size: 11.5px;
  color: #1f7a4d;
  cursor: pointer;
  white-space: nowrap;
}
.loc-r:hover {
  text-decoration: underline;
}
</style>
