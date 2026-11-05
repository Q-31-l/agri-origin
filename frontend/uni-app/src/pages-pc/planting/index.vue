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
          <text class="pc-th w-chain">上链</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无农事记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-date">{{ r.operationDate || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td w-type">{{ plantingTypeLabel(r.recordType) }}</text>
            <view class="pc-td w-content">{{ r.content || '-' }}</view>
            <text class="pc-td w-material">{{ r.materialName || '-' }}</text>
            <text class="pc-td w-dosage">{{ r.dosage || '-' }}</text>
            <text class="pc-td w-op">{{ r.operatorName || '-' }}</text>
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
              <text class="pc-label">所属批次<text class="req">*</text></text>
              <pc-select v-model="form.batchId" :options="batchOpts" placeholder="请选择批次" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">操作类型</text>
              <pc-select v-model="form.recordType" :options="typeOptions" width="100%" />
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
              <text class="pc-label">GPS 坐标</text>
              <input class="pc-input" v-model="form.gps" placeholder="如：113.26,23.13" />
            </view>
            <view class="pc-field full">
              <text class="pc-label">操作内容<text class="req">*</text></text>
              <textarea
                class="pc-textarea"
                v-model="form.content"
                placeholder="描述本次农事操作的具体内容"
                maxlength="300"></textarea>
            </view>
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
import { plantingPage, createPlanting, batchOptions } from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { PLANTING_TYPE_OPTIONS, plantingTypeLabel } from '@/config/constants'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const batches = ref([])

const query = reactive({ page: 1, size: 10, batchId: '', batchNo: '' })

const canCreate = computed(() => store.hasPerm('planting:create'))

const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

const typeOptions = PLANTING_TYPE_OPTIONS

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions()).data || []
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

const form = reactive({
  batchId: '',
  recordType: 'SOIL',
  operationDate: today(),
  content: '',
  materialName: '',
  dosage: '',
  gps: ''
})

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onDate(e) {
  form.operationDate = e.detail.value
}

async function openCreate() {
  await loadBatches()
  form.batchId = ''
  form.recordType = 'SOIL'
  form.operationDate = today()
  form.content = ''
  form.materialName = ''
  form.dosage = ''
  form.gps = ''
  createVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')
  if (!form.operationDate) return toast('请选择操作日期')
  if (!form.content.trim()) return toast('请填写操作内容')

  submitting.value = true
  try {
    await createPlanting({
      batchId: form.batchId,
      recordType: form.recordType,
      operationDate: form.operationDate,
      content: form.content,
      materialName: form.materialName,
      dosage: form.dosage,
      gps: form.gps
    })
    uni.showToast({ title: '记录已提交', icon: 'success' })
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

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-date { flex: 1; }
.w-batch { flex: 1.4; }
.w-type { flex: 1; }
.w-content { flex: 2.2; }
.w-material { flex: 1.1; }
.w-dosage { flex: 0.9; }
.w-op { flex: 0.9; }
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
