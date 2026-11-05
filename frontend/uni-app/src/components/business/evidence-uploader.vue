<template>
  <view class="ev-up">
    <view class="grid">
      <view v-for="(f, i) in list" :key="i" class="cell">
        <!-- 视频：内联首帧 + 播放角标；图片：缩略图 -->
        <video
          v-if="f.mediaType === 'VIDEO'"
          :src="urlOf(f)"
          class="thumb"
          :controls="false"
          :show-center-play-btn="false"
          object-fit="cover"
          @click="preview(f)" />
        <image v-else :src="urlOf(f)" mode="aspectFill" class="thumb" @click="preview(f)" />
        <view v-if="f.mediaType === 'VIDEO'" class="play" @click="preview(f)">▶</view>

        <view v-if="f.status === 'uploading'" class="mask">
          <text class="pct">{{ f.progress }}%</text>
        </view>
        <view v-else-if="f.status === 'error'" class="mask err" @click="remove(i)">
          <text>失败·移除</text>
        </view>
        <view class="del" @click.stop="remove(i)">×</view>
        <text class="kind">{{ f.mediaType === 'VIDEO' ? '视频' : '图片' }}</text>
      </view>

      <view v-if="list.length < max" class="cell add" @click="pick('image')">
        <text class="plus">＋</text>
        <text class="tip">图片实证</text>
      </view>
      <view v-if="list.length < max" class="cell add video" @click="pick('video')">
        <text class="plus">▶</text>
        <text class="tip">视频实证</text>
      </view>
    </view>
    <text class="note">图片与视频的 SHA-256 会并入本次上链摘要，事后无法替换而不被发现</text>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'
import { chooseImages, chooseVideos, uploadFile } from '@/utils/upload'
import { resolveFileUrl } from '@/utils/file'
import { toast } from '@/utils/ui'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 6 },
  bizType: { type: String, default: '' },
  bizId: { type: [Number, String], default: '' }
})
const emit = defineEmits(['update:modelValue', 'change'])

const list = ref([])

watch(
  () => props.modelValue,
  (v) => {
    if (!v || !v.length) return
    const doneCount = list.value.filter((x) => x.status === 'done').length
    if (v.length !== doneCount) {
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

function urlOf(f) {
  if (f.url) return f.url
  return f.filePath ? resolveFileUrl(f.filePath) : ''
}

async function pick(kind) {
  try {
    const items = []
    if (kind === 'video') {
      const vs = await chooseVideos({ maxDuration: 60 })
      for (const v of vs) {
        if (v.size && v.size > 100 * 1024 * 1024) {
          toast('视频不能超过 100MB，请压缩后再传')
          continue
        }
        items.push({ path: v.path, mediaType: 'VIDEO' })
      }
    } else {
      const paths = await chooseImages(Math.max(1, props.max - list.value.length))
      for (const p of paths) items.push({ path: p, mediaType: 'IMAGE' })
    }

    for (const it of items) {
      list.value.push({ url: it.path, mediaType: it.mediaType, status: 'uploading', progress: 0 })
      const idx = list.value.length - 1
      try {
        const res = await uploadFile({
          filePath: it.path,
          bizType: props.bizType,
          bizId: props.bizId,
          onProgress: (pg) => {
            if (list.value[idx]) list.value[idx].progress = pg
          }
        })
        list.value[idx] = {
          ...res,
          url: it.path,
          mediaType: res.mediaType || it.mediaType,
          status: 'done',
          progress: 100
        }
      } catch (e) {
        if (list.value[idx]) list.value[idx].status = 'error'
      }
      sync()
    }
  } catch (e) {
    if (!e.cancel) console.warn('[evidence-uploader]', e)
  }
}

function remove(i) {
  list.value.splice(i, 1)
  sync()
}

function preview(f) {
  if (f.status !== 'done') return
  if (f.mediaType === 'VIDEO') {
    // 视频：H5 直接全屏播放（浏览器原生），App/小程序交给 video 全屏
    // #ifdef H5
    const el = document.createElement('video')
    el.src = urlOf(f)
    el.controls = true
    el.autoplay = true
    el.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;background:#000;z-index:99999;object-fit:contain'
    const close = document.createElement('div')
    close.textContent = '✕'
    close.style.cssText =
      'position:fixed;top:18px;right:20px;z-index:100000;color:#fff;font-size:26px;cursor:pointer;text-shadow:0 1px 6px rgba(0,0,0,.6)'
    close.onclick = () => {
      el.pause()
      el.remove()
      close.remove()
    }
    document.body.appendChild(el)
    document.body.appendChild(close)
    // #endif
    // #ifndef H5
    toast('请点击视频区域播放')
    // #endif
    return
  }
  const images = list.value.filter((x) => x.mediaType !== 'VIDEO' && x.status === 'done').map(urlOf)
  uni.previewImage({ current: urlOf(f), urls: images.length ? images : [urlOf(f)] })
}

function sync() {
  const done = list.value.filter((x) => x.status === 'done')
  emit('update:modelValue', done)
  emit('change', done)
}

defineExpose({
  isUploading: () => list.value.some((x) => x.status === 'uploading'),
  ids: () => list.value.filter((x) => x.status === 'done').map((x) => x.id)
})
</script>

<style lang="scss" scoped>
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 14rpx;
}
.cell {
  width: 148rpx;
  height: 148rpx;
  border-radius: 12rpx;
  overflow: hidden;
  position: relative;
  background: #0f172a;
}
.thumb {
  width: 100%;
  height: 100%;
}
.play {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}
.mask.err {
  background: rgba(217, 48, 38, 0.72);
}
.mask text {
  color: #fff;
  font-size: 22rpx;
}
.del {
  position: absolute;
  right: 0;
  top: 0;
  width: 38rpx;
  height: 38rpx;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 28rpx;
  line-height: 34rpx;
  text-align: center;
  border-bottom-left-radius: 12rpx;
  z-index: 2;
}
.kind {
  position: absolute;
  left: 0;
  bottom: 0;
  font-size: 18rpx;
  color: #fff;
  background: rgba(0, 0, 0, 0.42);
  padding: 2rpx 10rpx;
  border-top-right-radius: 10rpx;
}
.add {
  background: #fafbfc;
  border: 1px dashed #cfd8e3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.add.video {
  border-color: #b9d7c6;
  background: #f4faf6;
}
.plus {
  font-size: 40rpx;
  color: #b6c0cc;
  line-height: 1;
}
.add.video .plus {
  color: #1f7a4d;
  font-size: 32rpx;
}
.tip {
  font-size: 20rpx;
  color: $text-4;
  margin-top: 8rpx;
}
.note {
  display: block;
  margin-top: 12rpx;
  font-size: 20rpx;
  color: $text-4;
  line-height: 1.5;
}
</style>
