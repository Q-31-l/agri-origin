<template>
  <pc-shell title="质量检测">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">质量检测</text>
          <text class="pc-sub">检测报告、判定结论与报告文件哈希全部上链，防止事后篡改</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 录入检测报告</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <pc-select
            v-model="query.result"
            :options="resultFilterOptions"
            placeholder="全部结论"
            width="140px"
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
          <text class="pc-th w-no">报告编号</text>
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th w-org">检测机构</text>
          <text class="pc-th w-date">检测日期</text>
          <text class="pc-th w-res">结论</text>
          <text class="pc-th">执行标准</text>
          <text class="pc-th w-file">报告文件</text>
          <text class="pc-th w-chain">上链</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无检测报告</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-no mono">{{ r.reportNo || '-' }}</text>
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td w-org">{{ r.inspectOrg || '-' }}</text>
            <text class="pc-td w-date">{{ r.inspectDate || '-' }}</text>
            <view class="pc-td w-res"><pc-status :value="r.inspectResult" type="quality" /></view>
            <text class="pc-td">{{ r.standardNo || '-' }}</text>
            <view class="pc-td w-file">
              <text v-if="r.filePath" class="pc-link" @click="openFile(r)">查看报告</text>
              <text v-else class="muted">未上传</text>
            </view>
            <view class="pc-td w-chain"><pc-status :value="r.chainStatus" type="chain" /></view>
            <view class="pc-td w-act">
              <text class="pc-link" @click="openDetail(r)">检测项</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <!-- 录入报告 -->
      <pc-modal
        v-model="createVisible"
        title="录入检测报告"
        width="640px"
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
              <text class="pc-label">报告编号<text class="req">*</text></text>
              <input class="pc-input" v-model="form.reportNo" placeholder="如：QC20251105001" />
            </view>
            <view class="pc-field">
              <text class="pc-label">检测日期<text class="req">*</text></text>
              <picker mode="date" :value="form.inspectDate" @change="onDate">
                <view class="pc-input picker-box">
                  <text :class="{ ph: !form.inspectDate }">{{ form.inspectDate || '请选择日期' }}</text>
                </view>
              </picker>
            </view>
            <view class="pc-field">
              <text class="pc-label">检测结论<text class="req">*</text></text>
              <pc-select v-model="form.inspectResult" :options="resultOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">执行标准</text>
              <input class="pc-input" v-model="form.standardNo" placeholder="如：GB 2763-2021" />
            </view>
            <view class="pc-field">
              <text class="pc-label">报告文件</text>
              <view class="upload-row">
                <text class="file-name">{{ form.fileName || '未选择文件' }}</text>
                <view class="pc-btn ghost sm" @click="pickReport">选择 PDF</view>
              </view>
            </view>
            <view class="pc-field full">
              <text class="pc-label">检测项（可选，逐项填写）</text>
              <view class="items">
                <view v-for="(it, i) in form.items" :key="i" class="item-row">
                  <input class="pc-input flex2" v-model="it.name" placeholder="检测项，如：农药残留" />
                  <input class="pc-input flex1" v-model="it.value" placeholder="检测值" />
                  <input class="pc-input flex1" v-model="it.unit" placeholder="单位" />
                  <pc-select v-model="it.result" :options="itemResultOptions" width="92px" />
                  <text class="del" @click="removeItem(i)">✕</text>
                </view>
                <text class="add-item" @click="addItem">＋ 添加检测项</text>
              </view>
            </view>
            <view class="pc-field full">
              <text class="pc-label">综合结论说明</text>
              <textarea class="pc-textarea" v-model="form.conclusion" placeholder="如：各项指标均符合标准要求" maxlength="200"></textarea>
            </view>
          </view>
        </view>
      </pc-modal>

      <!-- 检测项详情 -->
      <pc-modal v-model="detailVisible" title="检测项明细" width="560px" hide-footer>
        <view v-if="!detailItems.length" class="pc-none">该报告未录入检测项明细</view>
        <view v-else class="pc-tbody">
          <view class="pc-thead inner">
            <text class="pc-th">检测项</text>
            <text class="pc-th">检测值</text>
            <text class="pc-th">单位</text>
            <text class="pc-th">判定</text>
          </view>
          <view v-for="(it, i) in detailItems" :key="i" class="pc-tr">
            <text class="pc-td">{{ it.name || '-' }}</text>
            <text class="pc-td">{{ it.value || '-' }}</text>
            <text class="pc-td">{{ it.unit || '-' }}</text>
            <view class="pc-td">
              <text class="mini-tag" :class="it.result === 'PASS' ? 'ok' : 'bad'">
                {{ it.result === 'PASS' ? '合格' : it.result === 'FAIL' ? '不合格' : '-' }}
              </text>
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
import { qualityPage, createQuality, batchOptions } from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { resolveFileUrl } from '@/utils/file'
import { chooseFile, uploadFile } from '@/utils/upload'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const batches = ref([])

const query = reactive({ page: 1, size: 10, batchNo: '', result: '' })

const canCreate = computed(() => store.hasPerm('quality:create'))

const batchOpts = computed(() =>
  batches.value.map((b) => ({
    value: b.id,
    label: `${b.batchNo} · ${b.productName || ''}`
  }))
)

const resultOptions = [
  { value: 'PASS', label: '合格' },
  { value: 'FAIL', label: '不合格' }
]
const resultFilterOptions = [{ value: '', label: '全部结论' }, ...resultOptions]
const itemResultOptions = [
  { value: 'PASS', label: '合格' },
  { value: 'FAIL', label: '不合格' }
]

async function loadBatches() {
  if (batches.value.length) return
  try {
    batches.value = (await batchOptions({ status: 'HARVESTED' })).data || []
  } catch (e) {
    batches.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.batchNo) params.batchNo = query.batchNo.trim()
    if (query.result) params.result = query.result
    const res = await qualityPage(params)
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
  query.batchNo = ''
  query.result = ''
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
  form.inspectDate = e.detail.value
}

/* ---------------- 录入 ---------------- */
const createVisible = ref(false)
const submitting = ref(false)
const uploading = ref(false)

const form = reactive({
  batchId: '',
  reportNo: '',
  inspectDate: today(),
  inspectResult: 'PASS',
  standardNo: '',
  conclusion: '',
  fileName: '',
  filePath: '',
  fileHash: '',
  fileSize: 0,
  items: [{ name: '', value: '', unit: '', result: 'PASS' }]
})

function addItem() {
  form.items.push({ name: '', value: '', unit: '', result: 'PASS' })
}

function removeItem(i) {
  form.items.splice(i, 1)
  if (!form.items.length) addItem()
}

async function openCreate() {
  await loadBatches()
  form.batchId = ''
  form.reportNo = ''
  form.inspectDate = today()
  form.inspectResult = 'PASS'
  form.standardNo = ''
  form.conclusion = ''
  form.fileName = ''
  form.filePath = ''
  form.fileHash = ''
  form.fileSize = 0
  form.items = [{ name: '', value: '', unit: '', result: 'PASS' }]
  createVisible.value = true
}

async function pickReport() {
  if (uploading.value) return
  let f
  try {
    f = await chooseFile()
  } catch (e) {
    if (!e || !e.cancel) toast('未选择文件')
    return
  }
  if (!f) return
  uploading.value = true
  uni.showLoading({ title: '上传中…' })
  try {
    const res = await uploadFile({ filePath: f.path || f.tempFilePath, bizType: 'QUALITY' })
    form.fileName = res.fileName || f.name
    form.filePath = res.filePath || ''
    form.fileHash = res.fileHash || ''
    form.fileSize = res.fileSize || f.size || 0
    uni.showToast({ title: '上传成功', icon: 'success' })
  } catch (e) {
    /* 工具层已提示 */
  } finally {
    uploading.value = false
    uni.hideLoading()
  }
}

function openFile(r) {
  const url = resolveFileUrl(r.filePath)
  if (!url) return toast('报告文件不存在')
  if (typeof window !== 'undefined') window.open(url, '_blank')
}

async function submit() {
  if (submitting.value) return
  if (!form.batchId) return toast('请选择所属批次')
  if (!form.reportNo.trim()) return toast('请填写报告编号')
  if (!form.inspectDate) return toast('请选择检测日期')

  const items = form.items.filter((i) => i.name && i.name.trim())
  submitting.value = true
  try {
    await createQuality({
      batchId: form.batchId,
      reportNo: form.reportNo.trim(),
      inspectDate: form.inspectDate,
      inspectResult: form.inspectResult,
      standardNo: form.standardNo,
      conclusion: form.conclusion,
      itemsJson: items.length ? JSON.stringify(items) : '',
      fileName: form.fileName,
      filePath: form.filePath,
      fileHash: form.fileHash,
      fileSize: form.fileSize
    })
    uni.showToast({ title: '报告已录入', icon: 'success' })
    createVisible.value = false
    cache.invalidate('batch', 'quality')
    query.page = 1
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

/* ---------------- 检测项 ---------------- */
const detailVisible = ref(false)
const detailItems = ref([])

function openDetail(r) {
  const raw = r.itemsJson
  if (!raw) {
    detailItems.value = []
    detailVisible.value = true
    return
  }
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    detailItems.value = Array.isArray(parsed) ? parsed : []
  } catch (e) {
    detailItems.value = []
  }
  detailVisible.value = true
}

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-no { flex: 1.3; }
.w-org { flex: 1.9; }
.w-batch { flex: 1.5; }
.w-date { flex: 1; }
.w-res { flex: 0.9; }
.w-file { flex: 1; }
.w-chain { flex: 1; }
.w-act { flex: 0.8; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
.muted {
  color: #c3ccd6;
  font-size: 12px;
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
.upload-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.file-name {
  flex: 1;
  font-size: 11.5px;
  color: #64748b;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.item-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.item-row .flex2 {
  flex: 2;
}
.item-row .flex1 {
  flex: 1;
}
.del {
  font-size: 12px;
  color: #d93026;
  cursor: pointer;
  padding: 0 4px;
}
.add-item {
  font-size: 12px;
  color: #1f7a4d;
  cursor: pointer;
  padding: 4px 0;
}
.pc-thead.inner .pc-th {
  flex: 1;
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
