<template>
  <view class="form-page">
    <view class="group">
      <batch-picker v-model="form.batchId" label="批次" required status="IN_TRANSIT,ON_SALE" usable empty-hint="暂无可销售的批次
批次完成物流交接后，才会进入这里供登记销售" />

      <view class="row">
        <text class="label required">销售市场</text>
        <input v-model="form.marketName" class="input" placeholder="如 福州海峡批发市场" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label required">销售日期</text>
        <picker mode="date" :value="form.salesDate" @change="(e) => (form.salesDate = e.detail.value)">
          <text class="value">{{ form.salesDate }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label required">单价</text>
        <input v-model="form.price" class="input" type="digit" placeholder="0.00" placeholder-class="ph" />
        <text class="unit">元/{{ form.unit }}</text>
      </view>

      <view class="row">
        <text class="label required">销售数量</text>
        <input v-model="form.quantity" class="input" type="digit" placeholder="0.00" placeholder-class="ph" />
        <text class="unit">{{ form.unit }}</text>
      </view>

      <view class="row">
        <text class="label">计量单位</text>
        <picker mode="selector" :range="units" @change="(e) => (form.unit = units[e.detail.value])">
          <text class="value">{{ form.unit }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="group">
      <view class="row">
        <text class="label">采购方</text>
        <input v-model="form.buyerName" class="input" placeholder="采购商名称" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">联系电话</text>
        <input v-model="form.buyerPhone" class="input" type="number" placeholder="手机号" placeholder-class="ph" />
      </view>
    </view>

    <view class="summary">
      <text class="sum-label">预计销售额</text>
      <text class="sum-value">￥{{ amount }}</text>
    </view>

    <view class="group" style="padding:24rpx">
      <text class="label">现场实证（图片 / 视频）</text>
      <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" style="margin-top:16rpx" />
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交销售登记' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createSales } from '@/api'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { back } from '@/utils/router'

const cache = useCacheStore()
const units = ['kg', '吨', '斤', '箱', '件']
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  marketName: '',
  salesDate: today(),
  price: '',
  quantity: '',
  unit: 'kg',
  buyerName: '',
  buyerPhone: ''
})

function today() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const amount = computed(() => {
  const p = Number(form.price)
  const q = Number(form.quantity)
  if (!p || !q) return '0.00'
  return (p * q).toFixed(2)
})

const { submitting, submit } = useForm(
  (data) => createSales({ ...data, price: Number(data.price), quantity: Number(data.quantity), evidenceIds: uploader.value ? uploader.value.ids() : [] }),
  {
    successText: '销售登记成功',
    noticeType: 'SALES',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.marketName) return '请填写销售市场'
      if (!d.price || Number(d.price) <= 0) return '请填写有效单价'
      if (!d.quantity || Number(d.quantity) <= 0) return '请填写有效数量'
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
.input {
  flex: 1;
  text-align: right;
  font-size: 28rpx;
}
.unit {
  font-size: 24rpx;
  color: $text-3;
  margin-left: 10rpx;
}
.arrow {
  font-size: 30rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.ph {
  color: $text-4;
  font-size: 26rpx;
}
.summary {
  margin: 0 24rpx;
  background: $brand-light;
  border-radius: $radius;
  padding: 24rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.sum-label {
  font-size: 26rpx;
  color: $brand;
}
.sum-value {
  font-size: 40rpx;
  font-weight: 700;
  color: $brand;
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
