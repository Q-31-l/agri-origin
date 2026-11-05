<template>
  <view class="form-page">
    <view class="group">
      <batch-picker v-model="form.batchId" label="批次" required status="PLANTING" />

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
        <text class="unit">{{ form.unit }}</text>
      </view>

      <view class="row">
        <text class="label">质量等级</text>
        <picker mode="selector" :range="levels" @change="(e) => (form.qualityLevel = levels[e.detail.value])">
          <text class="value">{{ form.qualityLevel }} 级</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label">天气</text>
        <input v-model="form.weather" class="input" placeholder="如 晴 / 多云" placeholder-class="ph" />
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label">现场实证</text>
        <view class="uploader-wrap">
          <evidence-uploader ref="uploader" v-model="images" :max="6" />
        </view>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label">备注</text>
        <textarea v-model="form.remark" class="textarea" placeholder="补充说明" placeholder-class="ph" maxlength="200"></textarea>
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
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createHarvest } from '@/api'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { back } from '@/utils/router'

const cache = useCacheStore()
const uploader = ref(null)
const images = ref([])
const levels = ['A', 'B', 'C']

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

function today() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const { submitting, submit } = useForm(
  (data) => createHarvest({ ...data, quantity: Number(data.quantity), evidenceIds: uploader.value ? uploader.value.ids() : [] }),
  {
    successText: '采收登记成功',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.harvestDate) return '请选择采收日期'
      if (!d.quantity || Number(d.quantity) <= 0) return '请填写采收数量'
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
})

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
