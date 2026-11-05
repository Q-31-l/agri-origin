<template>
  <view class="edit-page">
    <view v-if="loading" class="loading">加载中…</view>
    <block v-else>
      <view class="group">
        <view class="row">
          <text class="label">批次编码</text>
          <text class="value mono">{{ detail.batch.batchNo }}</text>
        </view>
        <view class="row">
          <text class="label required">种植日期</text>
          <picker mode="date" :value="form.plantingDate" @change="(e) => (form.plantingDate = e.detail.value)">
            <text class="value">{{ form.plantingDate || '请选择' }}</text>
          </picker>
          <text class="arrow">›</text>
        </view>
        <view class="row">
          <text class="label required">预计产量</text>
          <input v-model="form.quantity" class="input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="unit">{{ form.unit }}</text>
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
          {{ submitting ? '保存中…' : '保存修改' }}
        </view>
      </view>
    </block>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { batchDetail, updateBatch } from '@/api'
import { useCacheStore } from '@/store/cache'
import { useForm } from '@/composables/useForm'
import { back } from '@/utils/router'

const cache = useCacheStore()
const loading = ref(true)
const detail = ref({ batch: {} })

const form = reactive({
  plantingDate: '',
  quantity: '',
  unit: 'kg',
  remark: '',
  version: 0
})

const { submitting, submit } = useForm(
  (data) => updateBatch(detail.value.batch.id, { ...data, quantity: Number(data.quantity) }),
  {
    successText: '保存成功',
    validate: (d) => {
      if (!d.plantingDate) return '请选择种植日期'
      if (!d.quantity || Number(d.quantity) <= 0) return '请填写有效产量'
      return ''
    },
    onSuccess: () => {
      cache.invalidate('batch:list', 'batch:detail')
      setTimeout(() => back(), 500)
    }
  }
)

onLoad(async (opt) => {
  try {
    const res = await batchDetail(opt.id)
    detail.value = res.data
    const b = res.data.batch
    form.plantingDate = b.plantingDate || ''
    form.quantity = b.quantity
    form.unit = b.unit || 'kg'
    form.remark = b.remark || ''
    form.version = b.version || 0
  } finally {
    loading.value = false
  }
})

function onSubmit() {
  submit(form)
}
</script>

<style lang="scss" scoped>
.edit-page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 180rpx;
}
.loading {
  text-align: center;
  padding: 100rpx;
  color: $text-3;
}
.group {
  background: #fff;
  margin: 24rpx;
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
  font-size: 32rpx;
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
  border-radius: 10rpx;
  padding: 16rpx;
  font-size: 26rpx;
}
.ph {
  color: $text-4;
  font-size: 26rpx;
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
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
}
.btn.disabled {
  opacity: 0.6;
}
</style>
