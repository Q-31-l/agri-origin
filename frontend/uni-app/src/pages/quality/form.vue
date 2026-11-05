<template>
  <view class="form-page">
    <view class="group">
      <batch-picker v-model="form.batchId" label="批次" required status="HARVESTED" />

      <view class="row">
        <text class="label required">报告编号</text>
        <input v-model="form.reportNo" class="input" placeholder="如 JC20261105-001" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label required">检测日期</text>
        <picker mode="date" :value="form.inspectDate" @change="(e) => (form.inspectDate = e.detail.value)">
          <text class="value">{{ form.inspectDate }}</text>
        </picker>
        <text class="arrow">›</text>
      </view>

      <view class="row">
        <text class="label">执行标准</text>
        <input v-model="form.standardNo" class="input" placeholder="如 GB 2763-2021" placeholder-class="ph" />
      </view>

      <view class="row">
        <text class="label required">检测结论</text>
        <view class="seg">
          <text class="seg-item" :class="{ on: form.inspectResult === 'PASS' }" @click="form.inspectResult = 'PASS'">合格</text>
          <text class="seg-item danger" :class="{ on: form.inspectResult === 'FAIL' }" @click="form.inspectResult = 'FAIL'">不合格</text>
        </view>
      </view>
    </view>

    <view class="group items-group">
      <view class="items-head">
        <text class="label">检测项</text>
        <text class="add-item" @click="addItem">＋ 添加</text>
      </view>

      <view v-for="(it, i) in items" :key="i" class="item">
        <view class="item-row">
          <input v-model="it.item" class="item-name" placeholder="检测项名称" placeholder-class="ph" />
          <text class="del" @click="removeItem(i)">删除</text>
        </view>
        <view class="item-row">
          <view class="mini">
            <text class="mini-label">实测值</text>
            <input v-model="it.value" class="mini-input" type="digit" placeholder="0" placeholder-class="ph" />
          </view>
          <view class="mini">
            <text class="mini-label">限量</text>
            <input v-model="it.limit" class="mini-input" type="digit" placeholder="0" placeholder-class="ph" />
          </view>
          <view class="mini">
            <text class="mini-label">单位</text>
            <input v-model="it.unit" class="mini-input" placeholder="mg/kg" placeholder-class="ph" />
          </view>
          <view class="mini">
            <text class="mini-label">判定</text>
            <picker mode="selector" :range="['合格', '不合格']" @change="(e) => (it.result = ['合格', '不合格'][e.detail.value])">
              <text class="mini-val" :class="{ bad: it.result === '不合格' }">{{ it.result }}</text>
            </picker>
          </view>
        </view>
      </view>

      <empty-state v-if="!items.length" text="请添加检测项目" />
    </view>

    <view class="group">
      <view class="col-item">
        <text class="label">综合结论说明</text>
        <textarea v-model="form.conclusion" class="textarea" placeholder="如：所检项目均符合 GB 2763-2021 限量要求" placeholder-class="ph" maxlength="200"></textarea>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <view class="between">
          <text class="label">检测报告文件</text>
          <text class="hint">支持 PDF / 图片，≤10MB</text>
        </view>
        <view v-if="file" class="file-card">
          <text class="file-name">{{ file.fileName }}</text>
          <text class="file-size">{{ fileSize(file.fileSize) }}</text>
          <text class="file-del" @click="file = null">移除</text>
        </view>
        <view v-else class="file-add" @click="pickFile">
          <text class="fa-icon">＋</text>
          <text class="fa-text">{{ uploading ? '上传中…' : '上传检测报告' }}</text>
        </view>
        <text v-if="file" class="hash mono">SHA-256 {{ shortHash(file.fileHash) }}</text>
      </view>
    </view>

    <view class="group">
      <view class="col-item">
        <view class="between">
          <text class="label">现场实证</text>
          <text class="hint">图片 / 视频</text>
        </view>
        <view class="uploader-wrap">
          <evidence-uploader ref="uploader" v-model="evidenceFiles" :max="6" />
        </view>
      </view>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '提交质检结果' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BatchPicker from '@/components/business/batch-picker.vue'
import EmptyState from '@/components/common/empty-state.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { createQuality } from '@/api'
import { chooseFile, uploadFile } from '@/utils/upload'
import { fileSize as fmtSize } from '@/utils/file'
import { shortHash } from '@/utils/format'
import { useForm } from '@/composables/useForm'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { back } from '@/utils/router'

const cache = useCacheStore()
const uploading = ref(false)
const file = ref(null)
const uploader = ref(null)
const evidenceFiles = ref([])

const form = reactive({
  batchId: '',
  reportNo: suggestNo(),
  inspectDate: today(),
  inspectResult: 'PASS',
  standardNo: 'GB 2763-2021',
  conclusion: ''
})

const items = ref([
  { item: '毒死蜱', value: '', limit: '0.02', unit: 'mg/kg', result: '合格' },
  { item: '铅', value: '', limit: '0.1', unit: 'mg/kg', result: '合格' },
  { item: '镉', value: '', limit: '0.05', unit: 'mg/kg', result: '合格' }
])

function today() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function suggestNo() {
  const d = new Date()
  const p = (n) => (n < 10 ? '0' + n : n)
  return `JC${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(Math.floor(Math.random() * 90) + 10)}`
}

function addItem() {
  items.value.push({ item: '', value: '', limit: '', unit: 'mg/kg', result: '合格' })
}
function removeItem(i) {
  items.value.splice(i, 1)
}

async function pickFile() {
  if (uploading.value) return
  try {
    const f = await chooseFile()
    uploading.value = true
    uni.showLoading({ title: '上传中', mask: true })
    const res = await uploadFile({ filePath: f.path || f.tempFilePath, bizType: 'QUALITY' })
    file.value = { ...res, fileName: res.fileName || f.name }
  } catch (e) {
    if (!e.cancel) toast('报告上传失败')
  } finally {
    uploading.value = false
    uni.hideLoading()
  }
}

const { submitting, submit } = useForm(
  (data) =>
    createQuality({
      ...data,
      itemsJson: JSON.stringify(
        items.value
          .filter((x) => x.item)
          .map((x) => ({ item: x.item, value: x.value, limit: x.limit, unit: x.unit, result: x.result }))
      ),
      fileName: file.value ? file.value.fileName : '',
      filePath: file.value ? file.value.filePath : '',
      fileHash: file.value ? file.value.fileHash : '',
      fileSize: file.value ? file.value.fileSize : null,
      evidenceIds: uploader.value ? uploader.value.ids() : []
    }),
  {
    successText: '质检结果已提交',
    validate: (d) => {
      if (!d.batchId) return '请选择批次'
      if (!d.reportNo) return '请填写报告编号'
      if (!d.inspectDate) return '请选择检测日期'
      if (!items.value.filter((x) => x.item).length) return '请至少填写一个检测项'
      return ''
    },
    onSuccess: () => {
      cache.invalidate('batch:list', 'batch:detail', 'quality:list')
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
.arrow {
  font-size: 30rpx;
  color: $text-4;
  margin-left: 8rpx;
}
.seg {
  flex: 1;
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  gap: 12rpx;
}
.seg-item {
  padding: 8rpx 26rpx;
  border-radius: 24rpx;
  background: #f4f6f8;
  color: $text-2;
  font-size: 24rpx;
}
.seg-item.on {
  background: $brand;
  color: #fff;
}
.seg-item.danger.on {
  background: $danger;
}
.items-group {
  padding-bottom: 16rpx;
}
.items-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  height: 90rpx;
}
.add-item {
  font-size: 24rpx;
  color: $brand;
}
.item {
  border-top: 1px solid $border;
  padding: 16rpx 0;
}
.item-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}
.item-name {
  flex: 1;
  height: 64rpx;
  background: #f8fafb;
  border-radius: 8rpx;
  padding: 0 14rpx;
  font-size: 26rpx;
}
.del {
  font-size: 22rpx;
  color: $danger;
  padding: 0 8rpx;
}
.mini {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.mini-label {
  font-size: 20rpx;
  color: $text-4;
}
.mini-input {
  height: 56rpx;
  background: #f8fafb;
  border-radius: 8rpx;
  padding: 0 12rpx;
  font-size: 24rpx;
  margin-top: 4rpx;
}
.mini-val {
  display: block;
  height: 56rpx;
  line-height: 56rpx;
  background: #f8fafb;
  border-radius: 8rpx;
  padding: 0 12rpx;
  font-size: 24rpx;
  margin-top: 4rpx;
  color: $success;
}
.mini-val.bad {
  color: $danger;
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
.between {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.hint {
  font-size: 22rpx;
  color: $text-4;
}
.file-card {
  margin-top: 16rpx;
  background: #f8fafb;
  border-radius: 12rpx;
  padding: 18rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.file-name {
  flex: 1;
  font-size: 24rpx;
  color: $text-1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.file-size {
  font-size: 22rpx;
  color: $text-4;
  margin: 0 16rpx;
}
.file-del {
  font-size: 22rpx;
  color: $danger;
}
.file-add {
  margin-top: 16rpx;
  height: 130rpx;
  border: 1px dashed #cfd8e3;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.fa-icon {
  font-size: 40rpx;
  color: #b6c0cc;
  line-height: 1;
}
.fa-text {
  font-size: 22rpx;
  color: $text-4;
  margin-top: 8rpx;
}
.hash {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $text-4;
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
