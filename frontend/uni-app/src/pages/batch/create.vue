<template>
  <view class="form-page">
    <view class="tip">
      <text class="tip-icon">i</text>
      <text class="tip-text">创建后系统会自动生成批次编码与溯源码，并把关键信息写入联盟链存证。</text>
    </view>

    <!-- 新注册的生产主体可能还没有地块：与其让人对着"暂无可选农场"卡住，
         不如允许直接指定一位农户建档（批次仍会落到该农户所在村） -->
    <view v-if="farmLoaded && !farms.length" class="tip warn">
      <text class="tip-icon">!</text>
      <text class="tip-text">你还没有登记地块。可以到电脑端「农场档案」先登记，也可以直接指定一位种植农户建档。</text>
    </view>

    <view class="group">
      <view class="row">
        <text class="label required">产品</text>
        <input
          v-model="form.productName"
          class="input"
          placeholder="手写产品名，如：草莓 / 玉米 / 高粱"
          placeholder-class="ph"
          maxlength="30"
          @input="onProductInput"
        />
        <text class="pick-link" @click="pickProduct">选品类</text>
      </view>

      <view class="row" @click="pickFarm">
        <text class="label">农场</text>
        <text class="value" :class="{ ph: !form.farmId }">{{ farmName || '请选择所属农场' }}</text>
        <text class="arrow">›</text>
      </view>

      <view v-if="!farms.length" class="row" @click="pickFarmer">
        <text class="label required">农户</text>
        <text class="value" :class="{ ph: !form.farmerId }">{{ farmerName || '没有地块时，直接指定种植农户' }}</text>
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
          <empty-state v-if="!products.length" text="暂无已有品类，可直接手写产品名" />
        </scroll-view>
      </view>
    </view>

    <!-- 农户选择（无地块时） -->
    <view v-if="showFarmer" class="mask" @click="showFarmer = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">选择种植农户</text>
          <text class="close" @click="showFarmer = false">×</text>
        </view>
        <scroll-view scroll-y class="sheet-list">
          <view v-for="f in farmers" :key="f.id" class="opt" @click="chooseFarmer(f)">
            <text class="opt-title">{{ f.realName }}</text>
            <text class="opt-sub">{{ villageName(f.villageId) }}</text>
          </view>
          <empty-state v-if="!farmers.length" text="暂无可选农户" />
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
import { createBatch, productList, farmList, userOptions, villageList } from '@/api'
import { useCacheStore } from '@/store/cache'
import { useForm } from '@/composables/useForm'
import { toast } from '@/utils/ui'
import { back } from '@/utils/router'
import EmptyState from '@/components/common/empty-state.vue'

const cache = useCacheStore()
const products = ref([])
const farms = ref([])
const farmers = ref([])
const villages = ref([])
const farmLoaded = ref(false)
const showProduct = ref(false)
const showFarm = ref(false)
const showFarmer = ref(false)
const units = ['kg', '吨', '斤', '箱', '件']

const form = reactive({
  productId: '',
  productName: '',
  farmId: '',
  farmerId: '',
  plantingDate: '',
  quantity: '',
  unit: 'kg',
  remark: ''
})

const farmName = computed(() => {
  const f = farms.value.find((x) => String(x.id) === String(form.farmId))
  return f ? f.farmName : ''
})
const farmerName = computed(() => {
  const f = farmers.value.find((x) => String(x.id) === String(form.farmerId))
  return f ? f.realName : ''
})

function villageName(id) {
  const v = villages.value.find((x) => String(x.id) === String(id))
  return v ? v.villageName : ''
}

const { submitting, submit } = useForm(
  (data) =>
    createBatch({
      ...data,
      quantity: Number(data.quantity),
      // 手写产品时 productId 为空，只传 productName；选了品类则两者都有
      productId: data.productId || null,
      productName: (data.productName || '').trim(),
      // 空字符串会被后端当成非法 Long，没选就明确传 null
      farmerId: data.farmerId || null,
      farmId: data.farmId || null
    }),
  {
    successText: '创建成功',
    validate: (d) => {
      if (!d.productId && !(d.productName || '').trim()) return '请填写或选择产品'
      if (!d.farmId && !d.farmerId) return '请选择农场或种植农户'
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
    const [p, f, u, v] = await Promise.allSettled([
      productList(),
      farmList(),
      userOptions({ roleCode: 'FARMER' }),
      villageList()
    ])
    if (p.status === 'fulfilled') products.value = p.value.data || []
    if (f.status === 'fulfilled') farms.value = f.value.data || []
    if (u.status === 'fulfilled') farmers.value = u.value.data || []
    if (v.status === 'fulfilled') villages.value = v.value.data || []
  } catch (e) {
    /* ignore */
  } finally {
    farmLoaded.value = true
  }
})

function pickProduct() {
  if (!products.value.length) return toast('暂无已有品类，可直接手写产品名')
  showProduct.value = true
}
function pickFarm() {
  if (!farms.value.length) return toast('暂无可选农场')
  showFarm.value = true
}
function chooseProduct(p) {
  form.productId = p.id
  form.productName = p.productName
  showProduct.value = false
}
/** 手写产品名后，清掉之前选中的品类 id，走"手写建批次" */
function onProductInput() {
  form.productId = ''
}
function chooseFarm(f) {
  form.farmId = f.id
  showFarm.value = false
}
function pickFarmer() {
  if (!farmers.value.length) return toast('暂无可选农户，请先在电脑端登记农户账号')
  showFarmer.value = true
}
function chooseFarmer(f) {
  form.farmerId = f.id
  showFarmer.value = false
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
/* 无地块提示：换成琥珀色，和普通说明区分开 */
.tip.warn {
  background: $warning-light;
}
.tip.warn .tip-text {
  color: #8a5a00;
}
.tip.warn .tip-icon {
  color: #8a5a00;
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
.pick-link {
  margin-left: 16rpx;
  font-size: 24rpx;
  color: $brand;
  flex-shrink: 0;
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
