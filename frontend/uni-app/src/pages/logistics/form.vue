<template>
  <view class="form-page">
    <view class="group">
      <batch-picker v-model="form.batchId" label="批次" required status="QUALIFIED,IN_TRANSIT" usable empty-hint="暂无可运输的批次
批次通过质量检测后，才会进入这里供登记运输" />

      <view class="row">
        <text class="label required">运单号</text>
        <input v-model="form.logisticsNo" class="input" placeholder="如 YD20261105001" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label">发货方</text>
        <input v-model="form.fromOrg" class="input" placeholder="启运地 / 发货单位" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label">收货方</text>
        <input v-model="form.toOrg" class="input" placeholder="目的地 / 收货单位" placeholder-class="ph" />
      </view>
    </view>

    <view class="group">
      <view class="row">
        <text class="label">车牌号</text>
        <input v-model="form.vehicleNo" class="input" placeholder="如 闽A12345" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">司机</text>
        <input v-model="form.driverName" class="input" placeholder="司机姓名" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">联系电话</text>
        <input v-model="form.driverPhone" class="input" type="number" placeholder="手机号" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">冷链温度</text>
        <input v-model="form.temperature" class="input" placeholder="如 4℃" placeholder-class="ph" />
      </view>
    </view>

    <view class="group">
      <view class="row">
        <text class="label required">发运时间</text>
        <picker mode="date" :value="departDate" @change="(e) => (departDate = e.detail.value)">
          <text class="value">{{ departDate }}</text>
        </picker>
        <picker mode="time" :value="departTime" @change="(e) => (departTime = e.detail.value)">
          <text class="value time">{{ departTime }}</text>
        </picker>
      </view>
      <view class="row">
        <text class="label">预计到达</text>
        <picker mode="date" :value="arriveDate" @change="(e) => (arriveDate = e.detail.value)">
          <text class="value" :class="{ ph: !arriveDate }">{{ arriveDate || '可选' }}</text>
        </picker>
        <picker mode="time" :value="arriveTime" @change="(e) => (arriveTime = e.detail.value)">
          <text class="value time">{{ arriveTime }}</text>
        </picker>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label">备注</text>
        <textarea v-model="form.remark" class="textarea" placeholder="运输说明" placeholder-class="ph" maxlength="200"></textarea>
      </view>
    </view>

    <view class="group" style="padding:24rpx">
      <text class="label">现场实证（图片 / 视频）</text>
      <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" style="margin-top:16rpx" />
    </view>

    <view class="tip">提交后该批次状态将变更为「运输中」，并写入链上存证。</view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交发运登记' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createLogistics } from '@/api'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { back } from '@/utils/router'

const cache = useCacheStore()
const departDate = ref(today())
const departTime = ref(nowTime())
const arriveDate = ref('')
const arriveTime = ref('')
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  logisticsNo: suggestNo(),
  fromOrg: '',
  toOrg: '',
  vehicleNo: '',
  driverName: '',
  driverPhone: '',
  temperature: '',
  status: 'DEPARTED',
  remark: ''
})

function pad(n) {
  return n < 10 ? '0' + n : n
}
function today() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function nowTime() {
  const d = new Date()
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function suggestNo() {
  const d = new Date()
  return `YD${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}`
}

const { submitting, submit } = useForm(
  (data) =>
    createLogistics({
      ...data,
      departTime: `${departDate.value} ${departTime.value}:00`,
      arriveTime: arriveDate.value ? `${arriveDate.value} ${arriveTime.value || '00:00'}:00` : null,
      evidenceIds: uploader.value ? uploader.value.ids() : []
    }),
  {
    successText: '发运登记成功',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.logisticsNo) return '请填写运单号'
      if (!departDate.value) return '请选择发运时间'
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
.value.ph {
  color: $text-4;
}
.value.time {
  flex: none;
  margin-left: 20rpx;
  color: $brand;
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
  height: 120rpx;
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
.tip {
  padding: 0 32rpx 24rpx;
  font-size: 22rpx;
  color: $text-3;
  line-height: 1.6;
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
