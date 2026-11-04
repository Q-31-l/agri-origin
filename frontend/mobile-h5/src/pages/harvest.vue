<template>
  <div class="page-plain">
    <van-nav-bar title="采收提交" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="banner">
      <div class="banner-title">采收信息登记</div>
      <div class="banner-sub">每个批次仅可提交一次采收信息，提交后自动上链存证</div>
      <van-button class="banner-btn" size="small" round icon="plus" @click="openForm()">新增采收</van-button>
    </div>

    <div class="list">
      <div v-for="r in rows" :key="r.id" class="item">
        <div class="item-top">
          <div class="date">
            <van-icon name="calendar-o" />
            <span>{{ r.harvestDate }}</span>
          </div>
          <span class="chain" :class="r.chainStatus">{{ chainLabel(r.chainStatus) }}</span>
        </div>

        <div class="qty">
          {{ r.quantity }}<span class="unit">{{ r.unit }}</span>
        </div>

        <div class="tags">
          <span class="tag">等级 {{ r.qualityLevel || '—' }}</span>
          <span class="tag" v-if="r.weather">{{ r.weather }}</span>
          <span class="tag" v-if="r.operatorName">{{ r.operatorName }}</span>
        </div>

        <div class="remark" v-if="r.remark">{{ r.remark }}</div>

        <div class="imgs" v-if="imagesOf(r).length">
          <van-image
            v-for="(u, i) in imagesOf(r)"
            :key="i"
            :src="u"
            width="64"
            height="64"
            radius="8"
            fit="cover"
            @click="preview(r, i)"
          />
        </div>

        <div class="item-foot" @click="$router.push(`/batch/${r.batchId}`)">
          <van-icon name="label-o" />
          <span class="mono">{{ r.batchNo }}</span>
          <span class="audit" :class="r.auditStatus">{{ auditLabel(r.auditStatus) }}</span>
          <van-icon name="arrow" class="arrow" />
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && !rows.length" description="暂无采收记录" />

    <div class="more" v-if="hasMore">
      <van-button size="small" plain round :loading="loading" @click="loadMore">加载更多</van-button>
    </div>

    <van-popup v-model:show="showForm" position="bottom" round :style="{ maxHeight: '92%' }">
      <div class="sheet">
        <div class="sheet-head">
          <span>新增采收信息</span>
          <van-icon name="cross" @click="showForm = false" />
        </div>

        <div class="sheet-body">
          <van-cell-group inset>
            <van-field
              v-model="form.batchLabel"
              is-link
              readonly
              required
              label="所属批次"
              placeholder="请选择批次"
              @click="batchPicker = true"
            />
            <van-field
              v-model="form.harvestDate"
              is-link
              readonly
              required
              label="采收日期"
              placeholder="请选择日期"
              @click="datePicker = true"
            />
            <van-field
              v-model="form.quantity"
              type="number"
              required
              label="采收数量"
              placeholder="请输入数量"
            >
              <template #button>
                <span class="unit-btn" @click="unitPicker = true">{{ form.unit }} <van-icon name="arrow-down" /></span>
              </template>
            </van-field>
            <van-field v-model="form.qualityLevel" is-link readonly label="质量等级" @click="levelPicker = true" />
            <van-field v-model="form.weather" label="天气情况" placeholder="如：晴，22℃（选填）" />
          </van-cell-group>

          <van-cell-group inset title="现场照片与备注">
            <van-field label="采收照片" :border="false">
              <template #input>
                <van-uploader v-model="fileList" :max-count="3" :after-read="afterRead" />
              </template>
            </van-field>
            <van-field
              v-model="form.remark"
              type="textarea"
              rows="2"
              autosize
              maxlength="150"
              show-word-limit
              label="备注"
              placeholder="选填"
            />
          </van-cell-group>
        </div>

        <div class="sheet-foot">
          <van-button type="primary" round block :loading="submitting" @click="onSubmit">
            提交并上链存证
          </van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="batchPicker" position="bottom" round>
      <van-picker
        :columns="batchColumns"
        title="选择批次"
        @confirm="onBatchConfirm"
        @cancel="batchPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="datePicker" position="bottom" round>
      <van-date-picker
        v-model="dateValue"
        title="选择采收日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="datePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="levelPicker" position="bottom" round>
      <van-picker
        :columns="levels"
        title="选择质量等级"
        @confirm="onLevelConfirm"
        @cancel="levelPicker = false"
      />
    </van-popup>

    <van-popup v-model:show="unitPicker" position="bottom" round>
      <van-picker
        :columns="unitColumns"
        title="选择计量单位"
        @confirm="onUnitConfirm"
        @cancel="unitPicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showImagePreview, showSuccessToast, showToast } from 'vant'
import { batchOptions, createHarvest, harvestPage, uploadFile } from '@/api'

const loading = ref(false)
const rows = ref([])
const page = ref(1)
const total = ref(0)
const hasMore = ref(false)

const showForm = ref(false)
const batchPicker = ref(false)
const datePicker = ref(false)
const levelPicker = ref(false)
const unitPicker = ref(false)
const submitting = ref(false)
const fileList = ref([])
const batches = ref([])

const today = new Date()
const minDate = new Date(today.getFullYear() - 2, 0, 1)
const maxDate = today
const dateValue = ref([
  String(today.getFullYear()),
  String(today.getMonth() + 1).padStart(2, '0'),
  String(today.getDate()).padStart(2, '0')
])

const levels = [
  { text: 'A 级（优）', value: 'A' },
  { text: 'B 级（良）', value: 'B' },
  { text: 'C 级（合格）', value: 'C' }
]

const unitColumns = [
  { text: 'kg 千克', value: 'kg' },
  { text: 't 吨', value: 't' },
  { text: '箱', value: '箱' },
  { text: '件', value: '件' }
]

const form = reactive({
  batchId: null,
  batchLabel: '',
  harvestDate: '',
  quantity: '',
  unit: 'kg',
  qualityLevel: 'A',
  weather: '',
  images: '',
  remark: ''
})

const batchColumns = computed(() =>
  batches.value.map((b) => ({ text: `${b.batchNo} · ${b.productName}`, value: b.id }))
)

const chainLabel = (s) => ({ SUCCESS: '已上链', FAILED: '上链失败', SKIPPED: '演示模式' }[s] || '未上链')
const auditLabel = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || '待审核')

const imagesOf = (r) => (r.images ? r.images.split(',').map((s) => s.trim()).filter(Boolean) : [])
const preview = (r, index) => showImagePreview({ images: imagesOf(r), startPosition: index })

const fetch = async (reset = false) => {
  loading.value = true
  try {
    if (reset) page.value = 1
    const res = await harvestPage({ page: page.value, size: 10 })
    const list = res.data.records || []
    rows.value = reset ? list : [...rows.value, ...list]
    total.value = res.data.total || 0
    hasMore.value = rows.value.length < total.value
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value += 1
  fetch(false)
}

const openForm = async () => {
  if (!batches.value.length) {
    try {
      const res = await batchOptions()
      batches.value = res.data || []
    } catch (e) {
      /* ignore */
    }
  }
  showForm.value = true
}

const onBatchConfirm = ({ selectedOptions }) => {
  const opt = selectedOptions[0]
  if (!opt) return
  form.batchId = opt.value
  form.batchLabel = opt.text
  batchPicker.value = false
}

const onDateConfirm = ({ selectedValues }) => {
  form.harvestDate = selectedValues.join('-')
  datePicker.value = false
}

const onLevelConfirm = ({ selectedOptions }) => {
  form.qualityLevel = selectedOptions[0].value
  levelPicker.value = false
}

const onUnitConfirm = ({ selectedOptions }) => {
  form.unit = selectedOptions[0].value
  unitPicker.value = false
}

const afterRead = async (item) => {
  const target = Array.isArray(item) ? item[0] : item
  target.status = 'uploading'
  target.message = '上传中'
  try {
    const fd = new FormData()
    fd.append('file', target.file)
    fd.append('bizType', 'HARVEST')
    const res = await uploadFile(fd)
    target.url = res.data.filePath
    target.status = 'done'
    target.message = ''
    syncImages()
  } catch (e) {
    target.status = 'failed'
    target.message = '上传失败'
  }
}

const syncImages = () => {
  form.images = fileList.value
    .filter((f) => f.status === 'done' && f.url)
    .map((f) => f.url)
    .join(',')
}

const onSubmit = async () => {
  if (!form.batchId) return showToast('请选择所属批次')
  if (!form.harvestDate) return showToast('请选择采收日期')
  if (!form.quantity || Number(form.quantity) <= 0) return showToast('请输入正确的采收数量')
  if (fileList.value.some((f) => f.status === 'uploading')) return showToast('照片仍在上传，请稍候')

  syncImages()
  submitting.value = true
  try {
    await createHarvest({
      batchId: form.batchId,
      harvestDate: form.harvestDate,
      quantity: Number(form.quantity),
      unit: form.unit || 'kg',
      qualityLevel: form.qualityLevel,
      weather: form.weather || undefined,
      images: form.images || undefined,
      remark: form.remark || undefined
    })
    showSuccessToast('采收信息提交成功')
    showForm.value = false
    resetForm()
    fetch(true)
  } catch (e) {
    /* ignore */
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.batchId = null
  form.batchLabel = ''
  form.harvestDate = ''
  form.quantity = ''
  form.qualityLevel = 'A'
  form.weather = ''
  form.images = ''
  form.remark = ''
  fileList.value = []
}

onMounted(() => fetch(true))
</script>

<style scoped>
.banner {
  margin: 12px 14px 0;
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #1f5c3e 0%, #2f8556 100%);
  color: #eaf6ef;
  position: relative;
}

.banner-title {
  font-size: 16px;
  font-weight: 700;
}

.banner-sub {
  font-size: 11px;
  color: rgba(207, 227, 216, 0.78);
  margin-top: 6px;
  line-height: 1.6;
  max-width: 78%;
}

.banner-btn {
  position: absolute;
  right: 16px;
  bottom: 16px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.28);
  color: #fff;
}

.list {
  padding: 12px 14px 0;
}

.item {
  background: #fff;
  border-radius: 14px;
  padding: 14px 15px;
  margin-bottom: 10px;
  box-shadow: 0 4px 14px rgba(31, 61, 45, 0.04);
}

.item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.date {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #3d4d5c;
}

.date .van-icon {
  color: var(--primary);
}

.chain {
  font-size: 10px;
  color: #8b9aaa;
  background: #f3f6f8;
  padding: 2px 7px;
  border-radius: 999px;
}

.chain.SUCCESS {
  color: #2f9e63;
  background: #eaf7f0;
}

.chain.FAILED {
  color: #e05252;
  background: #fdeeee;
}

.qty {
  font-size: 26px;
  font-weight: 700;
  color: var(--primary-dark);
  margin-top: 10px;
  line-height: 1;
}

.qty .unit {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-left: 4px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tag {
  font-size: 11px;
  color: #5a6b7b;
  background: #f4f7f5;
  padding: 3px 8px;
  border-radius: 6px;
}

.remark {
  font-size: 12px;
  color: #5a6b7b;
  margin-top: 9px;
  line-height: 1.7;
}

.imgs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.item-foot {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f5;
  font-size: 11px;
  color: #6b7c8f;
}

.audit {
  margin-left: auto;
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #fff6e8;
  color: #d99a2b;
}

.audit.APPROVED {
  background: #eaf7f0;
  color: #2f9e63;
}

.audit.REJECTED {
  background: #fdeeee;
  color: #e05252;
}

.item-foot .arrow {
  color: #c3ccd6;
}

.more {
  text-align: center;
  padding: 6px 0 20px;
}

.sheet {
  display: flex;
  flex-direction: column;
  max-height: 92vh;
}

.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px 10px;
  font-size: 16px;
  font-weight: 600;
}

.sheet-body {
  overflow-y: auto;
  flex: 1;
}

.sheet-foot {
  padding: 12px 18px calc(16px + env(safe-area-inset-bottom));
  border-top: 1px solid #f2f5f7;
}

.unit-btn {
  font-size: 13px;
  color: #3d4d5c;
}

:deep(.van-cell-group__title) {
  padding: 16px 30px 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

:deep(.van-field__label) {
  width: 84px;
  color: #3d4d5c;
}
</style>
