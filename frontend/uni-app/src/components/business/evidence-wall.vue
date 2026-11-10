<template>
  <view v-if="list.length" class="ev-wall" :class="{ compact }">
    <view class="head">
      <text class="cap">实证材料 · {{ list.length }} 份</text>
      <text v-if="chained" class="cap-chain">哈希已并入链上摘要</text>
    </view>

    <view class="grid">
      <template v-for="(f, i) in list" :key="f.id || i">
        <!-- 视频：内联播放器，直接可播 -->
        <view v-if="f.mediaType === 'VIDEO'" class="cell video-cell">
          <video
            :src="urlOf(f)"
            class="player"
            controls
            object-fit="contain"
            :show-fullscreen-btn="true"
            :show-play-btn="true"
            play-btn-position="center" />
          <view class="meta">
            <text class="m-name" @click="copy(f.hash)">视频 · {{ f.name || '现场视频' }}</text>
            <text v-if="f.hash" class="m-hash mono" @click="copy(f.hash)">SHA {{ short(f.hash) }}</text>
          </view>
          <view v-if="canRevoke" class="kill" @click.stop="onRevoke(f)">×</view>
        </view>

        <!-- 图片：缩略图，点击放大 -->
        <view v-else-if="f.mediaType === 'IMAGE'" class="cell img-cell">
          <image :src="urlOf(f)" mode="aspectFill" class="thumb" @click="preview(i)" />
          <view v-if="canRevoke" class="kill" @click.stop="onRevoke(f)">×</view>
        </view>

        <!-- 其它文件（报告 PDF 等）：下载入口 -->
        <view v-else class="cell file-cell" @click="open(f)">
          <text class="f-icon">PDF</text>
          <text class="f-name">{{ f.name || '附件' }}</text>
          <view v-if="canRevoke" class="kill" @click.stop="onRevoke(f)">×</view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { resolveFileUrl, fileSize } from '@/utils/file'
import { useUserStore } from '@/store/user'
import { revokeFile } from '@/api'
import { askReason, toast } from '@/utils/ui'

const store = useUserStore()
/** 只有持有 file:revoke 的角色（系统管理员）能看到撤销入口；公开溯源页无登录态，天然不显示 */
const canRevoke = computed(() => store.hasPerm('file:revoke'))

async function onRevoke(f) {
  const reason = await askReason(
    `撤销「${f.name || '该文件'}」`,
    '如：该照片为人像照，与批次无关'
  )
  if (!reason) {
    toast('未填写理由，已取消')
    return
  }
  try {
    await revokeFile(f.id, reason)
    toast('已撤销，该文件不再对外展示')
    emit('revoked', f)
  } catch (e) {
    /* 已提示 */
  }
}

const props = defineProps({
  items: { type: Array, default: () => [] },
  /** 是否标注"哈希已并入链上摘要"（有 txHash 的节点传 true） */
  chained: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['revoked'])

const list = computed(() => (props.items || []).filter((x) => x && (x.url || x.filePath)))

function urlOf(f) {
  return resolveFileUrl(f.url || f.filePath)
}

function preview(i) {
  const images = list.value.filter((x) => x.mediaType === 'IMAGE').map(urlOf)
  uni.previewImage({ current: urlOf(list.value[i]), urls: images })
}

function open(f) {
  // #ifdef H5
  window.open(urlOf(f), '_blank')
  // #endif
  // #ifndef H5
  uni.showToast({ title: '请在电脑端查看附件', icon: 'none' })
  // #endif
}

function short(h) {
  return h ? h.slice(0, 10) + '…' : ''
}

function copy(v) {
  if (!v) return
  uni.setClipboardData({
    data: String(v),
    success: () => uni.showToast({ title: '文件哈希已复制', icon: 'none' })
  })
}

// 便于父级展示文件体积（预留）
void fileSize
</script>

<style lang="scss" scoped>
.ev-wall {
  margin-top: 16rpx;
  background: #f8fafc;
  border: 1px solid #e8eef3;
  border-radius: 12rpx;
  padding: 16rpx;
}
.head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}
.cap {
  font-size: 22rpx;
  color: $text-3;
  font-weight: 500;
}
.cap-chain {
  font-size: 18rpx;
  color: #1f7a4d;
  background: #e8f7ee;
  padding: 2rpx 10rpx;
  border-radius: 999px;
}
.grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 12rpx;
}
.img-cell {
  width: 148rpx;
  height: 148rpx;
  border-radius: 10rpx;
  overflow: hidden;
  position: relative;
}
.thumb {
  width: 100%;
  height: 100%;
}
.video-cell {
  width: 100%;
  background: #0b1220;
  border-radius: 10rpx;
  overflow: hidden;
  position: relative;
}
.player {
  width: 100%;
  height: 320rpx;
}
.meta {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 14rpx;
}
.m-name {
  font-size: 20rpx;
  color: #cbd5e1;
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.m-hash {
  font-size: 18rpx;
  color: #64748b;
  margin-left: 12rpx;
}
.file-cell {
  width: 148rpx;
  height: 148rpx;
  border-radius: 10rpx;
  background: #fff;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8rpx;
  position: relative;
}
.f-icon {
  font-size: 24rpx;
  font-weight: 700;
  color: #d93026;
}
.f-name {
  margin-top: 8rpx;
  font-size: 18rpx;
  color: $text-3;
  text-align: center;
  overflow: hidden;
}
.mono {
  font-family: Consolas, monospace;
}

/* 管理员撤销入口：只在持有 file:revoke 时渲染，压在缩略图右上角 */
.kill {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 38rpx;
  height: 38rpx;
  line-height: 34rpx;
  text-align: center;
  border-radius: 50%;
  background: rgba(217, 48, 38, 0.92);
  color: #fff;
  font-size: 26rpx;
  font-weight: 600;
  z-index: 3;
}
</style>
