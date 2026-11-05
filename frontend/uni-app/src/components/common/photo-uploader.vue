<template>
  <view class="uploader">
    <view v-for="(f, i) in list" :key="i" class="item">
      <image :src="thumb(f)" mode="aspectFill" class="thumb" @click="preview(i)" />
      <view v-if="f.status === 'uploading'" class="mask">
        <text class="pct">{{ f.progress }}%</text>
      </view>
      <view v-else-if="f.status === 'error'" class="mask err" @click="retry(i)">
        <text>重试</text>
      </view>
      <view class="del" @click.stop="remove(i)">×</view>
    </view>
    <view v-if="list.length < max" class="add" @click="pick">
      <text class="plus">＋</text>
      <text class="tip">{{ tip }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { chooseImages, uploadFile } from '@/utils/upload'
import { resolveFileUrl } from '@/utils/file'
import { toast } from '@/utils/ui'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 3 },
  bizType: { type: String, default: '' },
  bizId: { type: [Number, String], default: '' },
  tip: { type: String, default: '拍照上传' }
})

const emit = defineEmits(['update:modelValue', 'change'])
const list = ref([])

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    // 外部回填（编辑场景）：只在长度变化时同步，避免覆盖上传中的项
    if (v.length && v.length !== list.value.filter((x) => x.status === 'done').length) {
      list.value = v.map((x) => ({
        ...x,
        url: x.filePath ? resolveFileUrl(x.filePath) : x.url,
        status: 'done',
        progress: 100
      }))
    }
  },
  { immediate: true }
)

function thumb(f) {
  if (f.url && /^(blob:|http|wxfile:|file:|_doc|\/)/.test(f.url)) return f.url
  return f.filePath ? resolveFileUrl(f.filePath) : f.url
}

async function pick() {
  try {
    const paths = await chooseImages(props.max - list.value.length)
    for (const p of paths) {
      const item = { url: p, status: 'uploading', progress: 0 }
      list.value.push(item)
      const idx = list.value.length - 1
      try {
        const res = await uploadFile({
          filePath: p,
          bizType: props.bizType,
          bizId: props.bizId,
          onProgress: (pg) => {
            if (list.value[idx]) list.value[idx].progress = pg
          }
        })
        list.value[idx] = { ...res, url: p, status: 'done', progress: 100 }
      } catch (e) {
        if (list.value[idx]) list.value[idx].status = 'error'
      }
    }
    sync()
  } catch (e) {
    if (!e.cancel) console.warn('[photo-uploader]', e)
  }
}

function remove(i) {
  list.value.splice(i, 1)
  sync()
}

function preview(i) {
  const urls = list.value.filter((x) => x.status === 'done').map(thumb)
  const cur = thumb(list.value[i])
  uni.previewImage({ current: cur, urls: urls.length ? urls : [cur] })
}

function retry(i) {
  const item = list.value[i]
  if (!item) return
  list.value.splice(i, 1)
  pick()
}

function sync() {
  const done = list.value.filter((x) => x.status === 'done')
  emit('update:modelValue', done)
  emit('change', done)
}

defineExpose({
  isUploading: () => list.value.some((x) => x.status === 'uploading'),
  count: () => list.value.length
})
</script>

<style lang="scss" scoped>
.uploader {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 16rpx;
}
.item,
.add {
  width: 160rpx;
  height: 160rpx;
  border-radius: 12rpx;
  overflow: hidden;
  position: relative;
}
.thumb {
  width: 100%;
  height: 100%;
}
.mask {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mask.err {
  background: rgba(217, 48, 38, 0.7);
}
.pct {
  color: #fff;
  font-size: 24rpx;
}
.mask text {
  color: #fff;
  font-size: 24rpx;
}
.del {
  position: absolute;
  right: 0;
  top: 0;
  width: 40rpx;
  height: 40rpx;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 30rpx;
  line-height: 36rpx;
  text-align: center;
  border-bottom-left-radius: 12rpx;
}
.add {
  border: 1px dashed #cfd8e3;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.plus {
  font-size: 44rpx;
  color: #b6c0cc;
  line-height: 1;
}
.tip {
  font-size: 20rpx;
  color: $text-4;
  margin-top: 8rpx;
}
</style>
