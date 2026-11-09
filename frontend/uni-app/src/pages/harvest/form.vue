<template>
  <view class="form-page">
    <view class="group">
      <batch-picker
        v-model="form.batchId"
        label="批次"
        required
        status="PLANTING"
        usable
        empty-hint="暂无可采收的批次
批次需经村管核验通过、且仍处于种植中，才会出现在这里"
        @change="onBatchChange" />

      <view class="row">
        <text class="label required">采收日期</text>
        <picker mode="date" :value="form.harvestDate" @change="(e) => (form.harvestDate = e.detail.value)">
          <text class="value">{{ form.harvestDate }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label required">采收数量</text>
        <input v-model="form.quantity" class="input" type="digit" placeholder="0.00" placeholder-class="ph" />
        <picker mode="selector" :range="units" @change="onUnitChange">
          <text class="unit-pick">{{ form.unit }} ▾</text>
        </picker>
      </view>

      <view class="row">
        <text class="label">质量等级</text>
        <picker mode="selector" :range="levels" :value="levelIndex" @change="onLevelChange">
          <text class="value">{{ form.qualityLevel }} 级</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label">天气</text>
        <picker mode="selector" :range="weathers" :value="weatherIndex" @change="onWeatherChange">
          <text class="value" :class="{ ph: !form.weather }">{{ form.weather || '自动获取中…' }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>
      <view v-if="weatherNote" class="note-row">
        <text class="note-t">{{ weatherNote }}</text>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <view class="between">
          <text class="label required">现场实证</text>
          <text class="hint">图片 / 视频，至少 1 个</text>
        </view>
        <view class="uploader-wrap">
          <evidence-uploader ref="uploader" v-model="images" :max="6" />
        </view>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label">备注</text>
        <textarea v-model="form.remark" class="textarea" placeholder="选填：采收方式、地块等补充说明" placeholder-class="ph" maxlength="200"></textarea>
      </view>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交采收登记' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createHarvest, currentWeather } from '@/api'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { WEATHER_OPTIONS } from '@/config/constants'
import { locate } from '@/utils/geo'
import { back } from '@/utils/router'

const cache = useCacheStore()
const uploader = ref(null)
const images = ref([])
const units = ['kg', '吨', '斤', '箱', '件']
const levels = ['A', 'B', 'C', '等外']
const weathers = WEATHER_OPTIONS.map((w) => w.label)

const form = reactive({
  batchId: '',
  harvestDate: today(),
  quantity: '',
  unit: 'kg',
  qualityLevel: 'A',
  weather: '',
  images: '',
  remark: ''
})

const batchInfo = ref(null)
const weatherNote = ref('')
const loadingWeather = ref(false)

const levelIndex = computed(() => Math.max(0, levels.indexOf(form.qualityLevel)))
const weatherIndex = computed(() => Math.max(0, weathers.indexOf(form.weather)))

function today() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function onUnitChange(e) {
  form.unit = units[e.detail.value]
}

function onLevelChange(e) {
  form.qualityLevel = levels[e.detail.value]
}

function onWeatherChange(e) {
  form.weather = weathers[e.detail.value]
  weatherNote.value = '已手动选择'
}

/**
 * 当日天气自动带出：先用设备定位取坐标，定位不可用时后端按请求 IP 兜底。
 * 两路都失败就留空让用户选，绝不默认填一个"晴"——采收天气是要留档的。
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
      // 定位失败不阻断，交给后端 IP 兜底
    }
    const res = await currentWeather(params)
    const w = res && res.data
    if (!w || !w.text) {
      weatherNote.value = '未取到天气，请手动选择'
      return
    }
    form.weather = w.text
    const temp = w.temperature != null ? ` ${Math.round(w.temperature)}℃` : ''
    // 不显示英文城市名，只强调"网络位置 + 可改"，让用户自己判断
    const from = w.locateBy === 'IP' ? '按网络位置' : '按当前位置'
    weatherNote.value = `${from}自动获取：${w.text}${temp} · 可手动修改`
  } catch (e) {
    weatherNote.value = '未取到天气，请手动选择'
  } finally {
    loadingWeather.value = false
  }
}

/** 换批次等于换地块，天气按新位置重取 */
function onBatchChange(b) {
  batchInfo.value = b
  autoWeather()
}

const { submitting, submit } = useForm(
  (data) =>
    createHarvest({
      ...data,
      quantity: Number(data.quantity),
      evidenceIds: uploader.value ? uploader.value.ids() : []
    }),
  {
    successText: '采收登记成功',
    noticeType: 'HARVEST',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.harvestDate) return '请选择采收日期'
      if (!d.quantity || Number(d.quantity) <= 0) return '请填写采收数量'
      if (!images.value.length) return '请上传现场实证（图片或视频至少 1 个）'
      return ''
    },
    onSuccess: () => {
      cache.invalidate('batch:list', 'batch:detail')
      setTimeout(() => back(), 600)
    }
  }
)

onLoad((opt) => {
  if (opt && opt.batchId) form.batchId = opt.batchId
  autoWeather()
})

function onSubmit() {
  if (uploader.value && uploader.value.isUploading()) return toast('照片/视频仍在上传，请稍候')
  submit(form)
}
</script>

<style lang="scss" scoped>
.form-page {
  min-height: 100vh;
  background: $bg-page;
  padding: 24rpx 0 180rpx;
}
.group {
  background: #fff;
  margin: 0 24rpx 24rpx;
  border-radius: $radius;
  padding: 0 24rpx;
  box-shadow: $shadow-card;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 100rpx;
  border-bottom: 1px solid $border;
}
.row:last-child {
  border-bottom: none;
}
.label {
  width: 170rpx;
  font-size: 28rpx;
  color: $text-2;
  flex-shrink: 0;
}
.required::before {
  content: '*';
  color: $danger;
  margin-right: 4rpx;
}
.value {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
}
.input {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
}
.unit {
  font-size: 26rpx;
  color: $text-3;
  margin-left: 12rpx;
}
.unit-pick {
  font-size: 26rpx;
  color: $text-1;
  padding: 8rpx 16rpx;
  background: #f4f7f9;
  border-radius: 8rpx;
  margin-left: 12rpx;
}
.note-row {
  padding: 0 0 18rpx;
}
.note-t {
  font-size: 21rpx;
  color: $text-4;
  line-height: 1.5;
}
.arrow {
  font-size: 30rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.col-item {
  padding: 24rpx 0;
}
.textarea {
  width: 100%;
  height: 140rpx;
  margin-top: 12rpx;
  background: #f8fafb;
  border-radius: 12rpx;
  padding: 16rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
  font-size: 26rpx;
}
.uploader-wrap {
  margin-top: 16rpx;
}
.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
}
.btn {
  height: 92rpx;
  border-radius: 46rpx;
  background: $brand-grad;
  color: #fff;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.disabled {
  opacity: 0.6;
}
</style>
