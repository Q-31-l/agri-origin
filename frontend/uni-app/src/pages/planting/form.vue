<template>
  <view class="form-page">
    <view class="group">
      <batch-picker v-model="form.batchId" label="批次" required :status="''" />

      <view class="row">
        <text class="label required">农事类型</text>
        <picker mode="selector" :range="types" @change="onTypeChange">
          <text class="value">{{ form.recordType || '请选择' }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label required">作业日期</text>
        <picker mode="date" :value="form.operationDate" @change="(e) => (form.operationDate = e.detail.value)">
          <text class="value" :class="{ ph: !form.operationDate }">{{ form.operationDate || '请选择日期' }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label">物料名称</text>
        <input v-model="form.materialName" class="input" placeholder="如 有机肥 / 生物农药" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label">用量</text>
        <input v-model="form.dosage" class="input" placeholder="如 20kg/亩" placeholder-class="ph" />
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label required">农事内容</text>
        <textarea
          v-model="form.content"
          class="textarea"
          placeholder="记录本次作业的具体内容，如施肥方式、灌溉时长、病虫害防治情况"
          placeholder-class="ph"
          maxlength="300"></textarea>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <view class="between">
          <text class="label">现场实证</text>
          <text class="hint">图片 / 视频</text>
        </view>
        <view class="uploader-wrap">
          <evidence-uploader ref="uploader" v-model="images" :max="6" />
        </view>
      </view>
    </view>

    <view class="group">
      <view class="row" @click="getGps">
        <text class="label">作业地点</text>
        <text class="value" :class="{ ph: !form.gps }">{{ form.gps || '点击获取当前位置' }}</text>
        <text class="arrow">◎</text>
      </view>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交农事记录' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createPlanting } from '@/api'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { PLANTING_TYPE_OPTIONS } from '@/config/constants'
import { back } from '@/utils/router'

const cache = useCacheStore()
const uploader = ref(null)
const images = ref([])

const typeOptions = PLANTING_TYPE_OPTIONS
const types = typeOptions.map((t) => t.label)

const form = reactive({
  batchId: '',
  recordType: '',
  operationDate: today(),
  content: '',
  materialName: '',
  dosage: '',
  images: '',
  gps: ''
})

function today() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const { submitting, submit } = useForm(
  (data) =>
    createPlanting({
      ...data,
      evidenceIds: uploader.value ? uploader.value.ids() : []
    }),
  {
    successText: '记录已提交',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.recordType) return '请选择农事类型'
      if (!d.operationDate) return '请选择作业日期'
      if (!d.content || d.content.trim().length < 2) return '请填写农事内容'
      return ''
    },
    onSuccess: () => {
      cache.invalidate('batch:list', 'batch:detail', 'planting:list')
      setTimeout(() => back(), 600)
    }
  }
)

onLoad((opt) => {
  if (opt && opt.batchId) form.batchId = opt.batchId
})

function onTypeChange(e) {
  form.recordType = typeOptions[e.detail.value].value
}

function getGps() {
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      form.gps = `${res.latitude.toFixed(6)},${res.longitude.toFixed(6)}`
      toast('位置已获取')
    },
    fail: () => toast('无法获取定位，请检查权限')
  })
}

function onSubmit() {
  if (uploader.value && uploader.value.isUploading()) return toast('照片仍在上传，请稍候')
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
  color: $text-1;
}
.value.ph {
  color: $text-4;
}
.arrow {
  font-size: 30rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.input {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
}
.col-item {
  padding: 24rpx 0;
}
.textarea {
  width: 100%;
  height: 180rpx;
  margin-top: 12rpx;
  background: #f8fafb;
  border-radius: 12rpx;
  padding: 16rpx;
  font-size: 26rpx;
  line-height: 1.6;
}
.ph {
  color: $text-4;
  font-size: 26rpx;
}
.hint {
  font-size: 22rpx;
  color: $text-4;
}
.uploader-wrap {
  margin-top: 16rpx;
}
.between {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
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
