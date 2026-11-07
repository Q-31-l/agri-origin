<template>
  <pc-shell title="采收登记">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">采收登记</text>
          <text class="pc-sub">采收量、等级与天气留痕，作为检测与发运的前置依据</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增采收记录</view>
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
          <text class="pc-th w-date">采收日期</text>
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th">采收量</text>
          <text class="pc-th">等级</text>
          <text class="pc-th">天气</text>
          <text class="pc-th w-remark">备注</text>
          <text class="pc-th">操作人</text>
          <text class="pc-th w-audit">审核</text>
          <text class="pc-th w-chain">上链</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无采收记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-date">{{ r.harvestDate || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td">{{ fmtQty(r.quantity, r.unit) }}</text>
            <text class="pc-td">{{ r.qualityLevel || '-' }}</text>
            <text class="pc-td">{{ r.weather || '-' }}</text>
            <view class="pc-td w-remark">{{ r.remark || '-' }}</view>
            <text class="pc-td">{{ r.operatorName || '-' }}</text>
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
        title="新增采收记录"
        width="600px"
        ok-text="提交并上链"
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
                empty-hint="暂无可采收的批次
批次需经村管核验通过、且仍处于种植中，才会出现在这里"
                @change="onBatchChange"
              />
            </view>
            <view class="pc-field">
              <text class="pc-label">采收日期<text class="req">*</text></text>
              <picker mode="date" :value="form.harvestDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.harvestDate }">{{ form.harvestDate || '请选择日期' }}</text>
                </view>
              </picker>
            </view>
            <view v-if="selectedBatch" class="pc-field full">
              <view class="binfo">
                <text class="binfo-t">{{ batchDesc(selectedBatch) || '未关联农场' }}</text>
                <pc-status :value="selectedBatch.status" type="batch" />
                <pc-status :value="selectedBatch.auditStatus" type="audit" />
              </view>
            </view>
            <view class="pc-field">
              <text class="pc-label">采收量<text class="req">*</text></text>
              <input class="pc-input" v-model="form.quantity" type="digit" placeholder="0.00" />
            </view>
            <view class="pc-field">
              <text class="pc-label">计量单位</text>
              <pc-select v-model="form.unit" :options="unitOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">质量等级</text>
              <pc-select v-model="form.qualityLevel" :options="levelOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">天气</text>
              <pc-select v-model="form.weather" :options="weatherOptions" placeholder="选择天气" width="100%" />
              <text class="wx-note">{{ weatherNote }}</text>
            </view>
            <view class="pc-field full">
              <text class="pc-label">备注</text>
              <textarea class="pc-textarea" v-model="form.remark" placeholder="选填：采收方式、地块等信息" maxlength="200"></textarea>
            </view>
            <view class="pc-field full">
              <text class="pc-label">现场实证（图片 / 视频）<text class="req">*</text></text>
              <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
              <text class="pc-hint">至少上传 1 个图片或视频；证据哈希会随本次记录一并上链。</text>
            </view>
          </view>
          <text class="pc-hint">提交后批次状态将推进为「已采收」，并写入联盟链存证。</text>
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
import { harvestPage, createHarvest, auditHarvest, batchOptions, currentWeather } from '@/api'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'
import { batchDesc, toBatchOption } from '@/utils/batch-label'
import { locate } from '@/utils/geo'
import { WEATHER_OPTIONS } from '@/config/constants'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
/** 查询下拉：全部批次，采收完的批次也要能回查 */
const batches = ref([])
/** 新增下拉：只放能采收的（种植中 + 已核验 + 无异常） */
const creatableBatches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('harvest:create'))
const canAudit = computed(() => store.hasPerm('harvest:audit'))

/**
 * 村级审核采收记录。与农事一致：后端 /harvest/{id}/audit 一直存在，
 * 但界面没有入口，harvest:audit 等于没生效。
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
      title: approve ? '审核通过' : '驳回采收记录',
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
    await auditHarvest(row.id, {
      auditStatus: approve ? 'APPROVED' : 'REJECTED',
      auditRemark: remark
    })
    toast(approve ? '已通过' : '已驳回')
    cache.invalidate('harvest:list')
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

const unitOptions = ['kg', '吨', '斤', '箱', '件'].map((u) => ({ value: u, label: u }))
const levelOptions = ['A', 'B', 'C', '等外'].map((u) => ({ value: u, label: u }))
const weatherOptions = WEATHER_OPTIONS

function fmtQty(v, unit) {
  return formatQty(v, unit || '')
}

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions()).data || []
  } catch (e) {
    batches.value = []
  }
}

/** 每次开弹窗都重新拉：别的端刚核验通过，状态随时会变 */
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
    const res = await harvestPage(params)
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

const createVisible = ref(false)
const submitting = ref(false)
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  harvestDate: today(),
  quantity: '',
  unit: 'kg',
  qualityLevel: 'A',
  weather: '',
  remark: ''
})

const selectedBatch = computed(
  () => creatableBatches.value.find((b) => String(b.id) === String(form.batchId)) || null
)

const weatherNote = ref('')
const loadingWeather = ref(false)

/**
 * 当日天气自动带出。
 *
 * 先用设备定位取坐标；定位被拒/不可用时后端按请求 IP 粗略定位兜底。
 * 两路都失败就留空并提示手选——绝不编一个"晴"填进去，采收记录的天气
 * 是要作为生产环境证据留存的，写错比空着更糟。
 */
async function autoWeather() {
  if (loadingWeather.value) return
  loadingWeather.value = true
  weatherNote.value = '正在获取当日天气…'
  try {
    let params = {}
    try {
      const pos = await locate({ timeout: 6000 })
      params = { lat: pos.lat, lng: pos.lng }
    } catch (e) {
      // 定位失败不阻断，交给后端按 IP 兜底
    }
    const res = await currentWeather(params)
    const w = res && res.data
    if (!w || !w.text) {
      weatherNote.value = '未取到天气，请手动选择'
      return
    }
    form.weather = w.text
    const temp = w.temperature != null ? ` ${Math.round(w.temperature)}℃` : ''
    // 不显示英文城市名（农户看不懂，也容易被误解成"就是这个城市"），
    // 只强调来源是网络位置 + 可改，让用户自己判断要不要动
    const from = w.locateBy === 'IP' ? '按网络位置' : '按当前位置'
    weatherNote.value = `${from}自动获取：${w.text}${temp} · 可手动修改`
  } catch (e) {
    weatherNote.value = '未取到天气，请手动选择'
  } finally {
    loadingWeather.value = false
  }
}

/** 换了批次等于换了地块，天气要按新位置重取 */
function onBatchChange() {
  autoWeather()
}

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onDate(e) {
  form.harvestDate = e.detail.value
}

async function openCreate() {
  await loadCreatableBatches()
  form.batchId = ''
  form.harvestDate = today()
  form.quantity = ''
  form.unit = 'kg'
  form.qualityLevel = 'A'
  form.weather = ''
  form.remark = ''
  evidenceFiles.value = []
  createVisible.value = true
  weatherNote.value = ''
  // 打开即自动取当日天气，用户不用点
  autoWeather()
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')
  if (!form.harvestDate) return toast('请选择采收日期')
  if (!form.quantity || Number(form.quantity) <= 0) return toast('请填写有效的采收量')
  if (uploader.value && uploader.value.isUploading()) return toast('实证文件仍在上传，请稍候')
  const ids = uploader.value ? uploader.value.ids() : []
  if (!ids.length) return toast('请上传现场实证（图片或视频至少 1 个）')

  submitting.value = true
  try {
    await createHarvest({
      batchId: form.batchId,
      harvestDate: form.harvestDate,
      quantity: Number(form.quantity),
      unit: form.unit,
      qualityLevel: form.qualityLevel,
      weather: form.weather,
      remark: form.remark,
      evidenceIds: ids
    })
    uni.showToast({ title: '采收已登记', icon: 'success' })
    createVisible.value = false
    cache.invalidate('batch', 'harvest')
    query.page = 1
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

// 查询下拉的候选批次进页面就备好，不能等点过"新增"才加载
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
.w-remark { flex: 1.8; }
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
.wx-note {
  font-size: 10.5px;
  color: #93a0ad;
  margin-top: 3px;
  line-height: 1.4;
}
</style>
