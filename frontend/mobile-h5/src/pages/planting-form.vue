<template>
  <div class="page-plain">
    <van-nav-bar title="新增农事记录" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="tip">
      <van-icon name="shield-o" />
      <span>提交后系统将计算记录摘要并写入联盟链，内容不可修改。如填写有误，请通过「更正说明」补充。</span>
    </div>

    <van-form @submit="onSubmit">
      <van-cell-group inset title="批次信息">
        <van-field
          v-model="batchLabel"
          is-link
          readonly
          required
          label="所属批次"
          placeholder="请选择批次"
          @click="batchPicker = true"
        />
        <van-field
          v-model="form.recordTypeLabel"
          is-link
          readonly
          required
          label="农事类型"
          placeholder="请选择农事类型"
          @click="typePicker = true"
        />
        <van-field
          v-model="form.operationDate"
          is-link
          readonly
          required
          label="作业日期"
          placeholder="请选择日期"
          @click="datePicker = true"
        />
      </van-cell-group>

      <van-cell-group inset title="作业详情">
        <van-field
          v-model="form.content"
          type="textarea"
          rows="3"
          autosize
          required
          maxlength="200"
          show-word-limit
          label="农事内容"
          placeholder="如实描述本次作业内容，如：施用有机肥并翻耕 20cm"
        />
        <van-field v-model="form.materialName" label="投入品名称" placeholder="如：复合肥 / 生物农药（选填）" />
        <van-field v-model="form.dosage" label="使用用量" placeholder="如：15kg/亩（选填）" />
      </van-cell-group>

      <van-cell-group inset title="现场取证">
        <van-field label="现场照片" :border="false">
          <template #input>
            <van-uploader v-model="fileList" :max-count="3" :after-read="afterRead" :before-delete="onDelete" />
          </template>
        </van-field>
        <van-field
          v-model="form.gps"
          label="定位坐标"
          readonly
          placeholder="点击右侧按钮自动获取"
          :border="false"
        >
          <template #button>
            <van-button size="small" plain type="primary" :loading="locating" @click="locate">
              {{ locating ? '获取中' : '获取定位' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>

      <div class="submit">
        <van-button type="primary" round block native-type="submit" :loading="submitting">
          提交并上链存证
        </van-button>
      </div>
    </van-form>

    <!-- 批次选择 -->
    <van-popup v-model:show="batchPicker" position="bottom" round>
      <van-picker
        :columns="batchColumns"
        title="选择批次"
        @confirm="onBatchConfirm"
        @cancel="batchPicker = false"
      />
    </van-popup>

    <!-- 农事类型 -->
    <van-popup v-model:show="typePicker" position="bottom" round>
      <van-picker
        :columns="typeColumns"
        title="选择农事类型"
        @confirm="onTypeConfirm"
        @cancel="typePicker = false"
      />
    </van-popup>

    <!-- 日期 -->
    <van-popup v-model:show="datePicker" position="bottom" round>
      <van-date-picker
        v-model="dateValue"
        title="选择作业日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="datePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { batchOptions, createPlanting, uploadFile } from '@/api'

const route = useRoute()
const router = useRouter()

const form = reactive({
  batchId: null,
  recordType: '',
  recordTypeLabel: '',
  operationDate: '',
  content: '',
  materialName: '',
  dosage: '',
  images: '',
  gps: ''
})

const batchLabel = ref('')
const batches = ref([])
const batchPicker = ref(false)
const typePicker = ref(false)
const datePicker = ref(false)
const locating = ref(false)
const submitting = ref(false)
const fileList = ref([])

const today = new Date()
const minDate = new Date(today.getFullYear() - 3, 0, 1)
const maxDate = today
const dateValue = ref([
  String(today.getFullYear()),
  String(today.getMonth() + 1).padStart(2, '0'),
  String(today.getDate()).padStart(2, '0')
])

const TYPES = [
  { text: '整地施肥', value: 'SOIL' },
  { text: '播种定植', value: 'SOWING' },
  { text: '追肥', value: 'FERTILIZE' },
  { text: '灌溉', value: 'IRRIGATE' },
  { text: '病虫害防治', value: 'PEST' },
  { text: '除草', value: 'WEED' },
  { text: '其他农事', value: 'OTHER' }
]

const batchColumns = computed(() =>
  batches.value.map((b) => ({ text: `${b.batchNo} · ${b.productName}`, value: b.id }))
)
const typeColumns = TYPES.map((t) => ({ text: t.text, value: t.value }))

const onBatchConfirm = ({ selectedOptions }) => {
  const opt = selectedOptions[0]
  if (!opt) return
  form.batchId = opt.value
  batchLabel.value = opt.text
  batchPicker.value = false
}

const onTypeConfirm = ({ selectedOptions }) => {
  const opt = selectedOptions[0]
  if (!opt) return
  form.recordType = opt.value
  form.recordTypeLabel = opt.text
  typePicker.value = false
}

const onDateConfirm = ({ selectedValues }) => {
  form.operationDate = selectedValues.join('-')
  datePicker.value = false
}

const afterRead = async (item) => {
  const file = Array.isArray(item) ? item[0].file : item.file
  const target = Array.isArray(item) ? item[0] : item
  target.status = 'uploading'
  target.message = '上传中'
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizType', 'PLANTING')
    const res = await uploadFile(fd)
    target.url = res.data.filePath
    target.status = 'done'
    target.message = ''
    target.fileHash = res.data.fileHash
    syncImages()
  } catch (e) {
    target.status = 'failed'
    target.message = '上传失败'
  }
}

const onDelete = () => {
  setTimeout(syncImages, 0)
  return true
}

const syncImages = () => {
  form.images = fileList.value
    .filter((f) => f.status === 'done' && f.url)
    .map((f) => f.url)
    .join(',')
}

const locate = () => {
  if (!navigator.geolocation) {
    showToast('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      form.gps = `${pos.coords.longitude.toFixed(6)},${pos.coords.latitude.toFixed(6)}`
      locating.value = false
      showSuccessToast('定位成功')
    },
    () => {
      locating.value = false
      showToast('定位失败，请检查权限')
    },
    { enableHighAccuracy: true, timeout: 8000 }
  )
}

const onSubmit = async () => {
  if (!form.batchId) return showToast('请选择所属批次')
  if (!form.recordType) return showToast('请选择农事类型')
  if (!form.operationDate) return showToast('请选择作业日期')
  if (!form.content.trim()) return showToast('请填写农事内容')
  if (fileList.value.some((f) => f.status === 'uploading')) return showToast('照片仍在上传，请稍候')

  syncImages()
  submitting.value = true
  try {
    await createPlanting({
      batchId: form.batchId,
      recordType: form.recordType,
      operationDate: form.operationDate,
      content: form.content.trim(),
      materialName: form.materialName || undefined,
      dosage: form.dosage || undefined,
      images: form.images || undefined,
      gps: form.gps || undefined
    })
    showSuccessToast('提交成功')
    router.replace('/planting')
  } catch (e) {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await batchOptions()
    batches.value = res.data || []
    const preset = Number(route.params.batchId)
    if (preset && preset > 0) {
      const hit = batches.value.find((b) => b.id === preset)
      if (hit) {
        form.batchId = hit.id
        batchLabel.value = `${hit.batchNo} · ${hit.productName}`
      }
    }
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.tip {
  display: flex;
  gap: 8px;
  margin: 12px 14px 0;
  padding: 12px 14px;
  background: #eef7f1;
  border-radius: 12px;
  color: #1f7a4c;
  font-size: 11px;
  line-height: 1.7;
}

.tip .van-icon {
  font-size: 14px;
  margin-top: 1px;
  flex-shrink: 0;
}

.submit {
  padding: 20px 18px calc(24px + env(safe-area-inset-bottom));
}

.submit :deep(.van-button) {
  height: 46px;
  font-size: 15px;
  font-weight: 600;
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
