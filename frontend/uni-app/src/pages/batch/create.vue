<template>
  <view class="form-page">
    <view class="tip">
      <text class="tip-icon">i</text>
      <text class="tip-text">创建后系统会自动生成批次编码与溯源码，并把关键信息写入联盟链存证。</text>
    </view>

    <view class="group">
      <view class="row" @click="pickProduct">
        <text class="label required">产品</text>
        <text class="value" :class="{ ph: !form.productId }">{{ productName || '请选择产品品类' }}</text>
        <text class="arrow">›</text>
      </view>

      <view class="row" @click="pickFarm">
        <text class="label">农场</text>
        <text class="value" :class="{ ph: !form.farmId }">{{ farmName || '请选择所属农场' }}</text>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label required">种植日期</text>
        <picker mode="date" :value="form.plantingDate" @change="(e) => (form.plantingDate = e.detail.value)">
          <text class="value" :class="{ ph: !form.plantingDate }">{{ form.plantingDate || '请选择日期' }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label required">预计产量</text>
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
      <view class="col-item">
        <text class="label">备注</text>
        <textarea v-model="form.remark" class="textarea" placeholder="可填写种植方式、地块编号等信息" placeholder-class="ph" maxlength="200"></textarea>
      </view>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交并上链存证' }}
      </view>
    </view>

    <!-- 产品选择 -->
    <view v-if="showProduct" class="mask" @click="showProduct = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">选择产品</text>
          <text class="close" @click="showProduct = false">×</text>
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view v-for="p in products" :key="p.id" class="opt" @click="chooseProduct(p)">
            <text class="opt-title">{{ p.productName }}</text>
            <text class="opt-sub">{{ p.category || '' }} {{ p.variety ? '· ' + p.variety : '' }}</text>
          </view>
          <empty-state v-if="!products.length" text="暂无产品品类，请联系管理员维护" />
        </scroll-view>
      </view>
    </view>

    <!-- 农场选择 -->
    <view v-if="showFarm" class="mask" @click="showFarm = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">选择农场</text>
          <text class="close" @click="showFarm = false">×</text>
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view v-for="f in farms" :key="f.id" class="opt" @click="chooseFarm(f)">
            <text class="opt-title">{{ f.farmName }}</text>
            <text class="opt-sub">{{ f.villageName || '' }} {{ f.address || '' }}</text>
          </view>
          <empty-state v-if="!farms.length" text="暂无可选农场" />
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createBatch, productList, farmList } from '@/api'
import { useCacheStore } from '@/store/cache'
import { useForm } from '@/composables/useForm'
import { toast } from '@/utils/ui'
import { back } from '@/utils/router'
import EmptyState from '@/components/common/empty-state.vue'

const cache = useCacheStore()
const products = ref([])
const farms = ref([])
const showProduct = ref(false)
const showFarm = ref(false)
const units = ['kg', '吨', '斤', '箱', '件']

const form = reactive({
  productId: '',
  farmId: '',
  plantingDate: '',
  quantity: '',
  unit: 'kg',
  remark: ''
})

const productName = computed(() => {
  const p = products.value.find((x) => String(x.id) === String(form.productId))
  return p ? p.productName : ''
})
const farmName = computed(() => {
  const f = farms.value.find((x) => String(x.id) === String(form.farmId))
  return f ? f.farmName : ''
})

const { submitting, submit } = useForm(
  (data) =>
    createBatch({
      ...data,
      quantity: Number(data.quantity)
    }),
  {
    successText: '创建成功',
    validate: (d) => {
      if (!d.productId) return '请选择产品品类'
      if (!d.plantingDate) return '请选择种植日期'
      if (!d.quantity || Number(d.quantity) <= 0) return '请填写有效的预计产量'
      return ''
    },
    onSuccess: () => {
      cache.invalidate('batch:list')
      setTimeout(() => back(), 600)
    }
  }
)

onLoad(async () => {
  try {
    const [p, f] = await Promise.allSettled([productList(), farmList()])
    if (p.status === 'fulfilled') products.value = p.value.data || []
    if (f.status === 'fulfilled') farms.value = f.value.data || []
  } catch (e) {
    /* ignore */
  }
})

function pickProduct() {
  if (!products.value.length) return toast('暂无产品品类')
  showProduct.value = true
}
function pickFarm() {
  if (!farms.value.length) return toast('暂无可选农场')
  showFarm.value = true
}
function chooseProduct(p) {
  form.productId = p.id
  showProduct.value = false
}
function chooseFarm(f) {
  form.farmId = f.id
  showFarm.value = false
}

function onSubmit() {
  submit(form)
}


</script>

<style lang="scss" scoped>
.form-page {
  min-height: 100vh;
  background: $bg-page;
  padding-bottom: 180rpx;
}
.tip {
  margin: 24rpx;
  background: $brand-light;
  border-radius: 12rpx;
  padding: 18rpx 20rpx;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}
.tip-icon {
  width: 28rpx;
  height: 28rpx;
  border-radius: 14rpx;
  background: $brand;
  color: #fff;
  font-size: 20rpx;
  text-align: center;
  line-height: 28rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
  margin-top: 2rpx;
}
.tip-text {
  flex: 1;
  font-size: 22rpx;
  color: $brand;
  line-height: 1.6;
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
  font-size: 28rpx;
  color: $text-1;
  text-align: right;
}
.value.ph {
  color: $text-4;
}
.arrow {
  font-size: 32rpx;
  color: $text-4;
  margin-left: 8rpx;
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
.ph {
  color: $text-4;
  font-size: 26rpx;
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
.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.04);
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
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 900;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.sheet {
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  max-height: 70vh;
}
.sheet-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx;
  border-bottom: 1px solid $border;
}
.sheet-title {
  font-size: 30rpx;
  font-weight: 600;
}
.close {
  font-size: 40rpx;
  color: $text-3;
  line-height: 1;
}
.sheet-list {
  max-height: 60vh;
}
.opt {
  padding: 24rpx 28rpx;
  border-bottom: 1px solid #f2f5f8;
}
.opt-title {
  font-size: 28rpx;
  color: $text-1;
}
.opt-sub {
  display: block;
  font-size: 22rpx;
  color: $text-3;
  margin-top: 4rpx;
}
</style>
