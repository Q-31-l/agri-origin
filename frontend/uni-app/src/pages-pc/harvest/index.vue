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
              <pc-select v-model="form.batchId" :options="batchOpts" placeholder="请选择批次" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">采收日期<text class="req">*</text></text>
              <picker mode="date" :value="form.harvestDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.harvestDate }">{{ form.harvestDate || '请选择日期' }}</text>
                </view>
              </picker>
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
              <input class="pc-input" v-model="form.weather" placeholder="如：晴、多云" />
            </view>
            <view class="pc-field full">
              <text class="pc-label">备注</text>
              <textarea class="pc-textarea" v-model="form.remark" placeholder="可填写采收方式、地块等信息" maxlength="200"></textarea>
            </view>
          </view>
          <text class="pc-hint">提交后批次状态将推进为「已采收」，并写入联盟链存证。</text>
            <view class="pc-field full">
              <text class="pc-label">现场实证（图片 / 视频）</text>
              <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
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
import { harvestPage, createHarvest, batchOptions } from '@/api'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const batches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('harvest:create'))

const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

const unitOptions = ['kg', '吨', '斤', '箱', '件'].map((u) => ({ value: u, label: u }))
const levelOptions = ['A', 'B', 'C', '等外'].map((u) => ({ value: u, label: u }))

function fmtQty(v, unit) {
  return formatQty(v, unit || '')
}

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions({ status: 'PLANTING' })).data || []
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

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onDate(e) {
  form.harvestDate = e.detail.value
}

async function openCreate() {
  await loadBatches()
  form.batchId = ''
  form.harvestDate = today()
  form.quantity = ''
  form.unit = 'kg'
  form.qualityLevel = 'A'
  form.weather = ''
  form.remark = ''
  createVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')
  if (!form.harvestDate) return toast('请选择采收日期')
  if (!form.quantity || Number(form.quantity) <= 0) return toast('请填写有效的采收量')

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
      evidenceIds: uploader.value ? uploader.value.ids() : []
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

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-date { flex: 1.1; }
.w-batch { flex: 1.5; }
.w-remark { flex: 1.8; }
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
</style>
