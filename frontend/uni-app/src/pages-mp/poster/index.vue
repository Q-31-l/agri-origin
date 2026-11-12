<template>
  <view class="poster-page">
    <view v-if="!batch.id" class="loading">数据加载中…</view>

    <block v-else>
      <!-- 画布（离屏渲染，尺寸按 750×1200 设计稿） -->
      <canvas
        canvas-id="posterCanvas"
        id="posterCanvas"
        class="canvas"
        :style="{ width: canvasW + 'px', height: canvasH + 'px' }"
      ></canvas>

      <!-- 预览 -->
      <view class="preview">
        <image v-if="posterPath" :src="posterPath" class="poster-img" mode="widthFix" @click="previewPoster" />
        <view v-else class="ph">
          <text class="ph-icon">▤</text>
          <text class="ph-text">正在生成海报…</text>
        </view>
      </view>

      <view class="tips">
        <text class="tip-line">· 海报包含产品信息、溯源码与扫码溯源二维码</text>
        <text class="tip-line">· 可保存到相册后打印张贴或转发给消费者</text>
      </view>

      <view class="actions">
        <view class="btn ghost" :class="{ disabled: !posterPath }" @click="saveToAlbum">保存到相册</view>
        <view class="btn" :class="{ disabled: !posterPath }" @click="previewPoster">预览大图</view>
      </view>
    </block>
  </view>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { batchDetail, traceQuery } from '@/api'
import { ENV } from '@/config/env'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'
import { readWindowInfo } from '@/utils/system'

const W = 750
const H = 1200

const batch = ref({})
const posterPath = ref('')
const canvasW = ref(375)
const canvasH = ref(600)

const sys = readWindowInfo()
const dpr = sys.pixelRatio || 2

const traceCode = computed(() => batch.value.traceCode || '')

function setCanvasSize() {
  canvasW.value = Math.round(W / 2)
  canvasH.value = Math.round(H / 2)
}

function downloadImage(url) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => (res.statusCode === 200 ? resolve(res.tempFilePath) : reject(res)),
      fail: reject
    })
  })
}

function drawRoundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

async function buildPoster() {
  const ctx = uni.createCanvasContext('posterCanvas')
  const b = batch.value

  // 背景
  ctx.setFillStyle('#f4f7f5')
  ctx.fillRect(0, 0, W, H)

  // 顶部品牌区
  const grad = ctx.createLinearGradient(0, 0, W, 420)
  grad.addColorStop(0, '#14432c')
  grad.addColorStop(1, '#1f7a4d')
  ctx.setFillStyle(grad)
  ctx.fillRect(0, 0, W, 420)

  ctx.setFillStyle('#ffffff')
  ctx.setFontSize(44)
  ctx.setTextAlign('left')
  ctx.fillText('农安链溯源', 48, 96)

  ctx.setFillStyle('rgba(255,255,255,0.62)')
  ctx.setFontSize(22)
  ctx.fillText('AGRI-ORIGIN  ·  区块链农产品溯源', 48, 134)

  // 产品名
  ctx.setFillStyle('#ffffff')
  ctx.setFontSize(56)
  ctx.fillText(b.productName || '农产品', 48, 250)

  ctx.setFillStyle('rgba(255,255,255,0.8)')
  ctx.setFontSize(26)
  ctx.fillText(`${b.variety || ''}${b.villageName ? ' · ' + b.villageName : ''}`, 48, 300)

  // 溯源码胶囊
  ctx.setFillStyle('rgba(255,255,255,0.18)')
  drawRoundRect(ctx, 48, 330, 420, 54, 27)
  ctx.fill()
  ctx.setFillStyle('#ffffff')
  ctx.setFontSize(24)
  ctx.fillText(`溯源码 ${b.traceCode || '-'}`, 70, 365)

  // 白色主卡片
  ctx.setFillStyle('#ffffff')
  drawRoundRect(ctx, 40, 380, W - 80, 520, 24)
  ctx.fill()

  // 关键字段
  const rows = [
    ['批次编码', b.batchNo || '-'],
    ['产品品种', `${b.productName || '-'}${b.variety ? ' / ' + b.variety : ''}`],
    ['产地农场', b.farmName || '-'],
    ['种植农户', b.farmerName || '-'],
    ['种植日期', b.plantingDate || '-'],
    ['采收日期', b.harvestDate || '-'],
    ['批      量', formatQty(b.quantity, b.unit)]
  ]
  let y = 440
  rows.forEach(([k, v]) => {
    ctx.setFillStyle('#94a3b8')
    ctx.setFontSize(24)
    ctx.fillText(k, 76, y)
    ctx.setFillStyle('#1f2937')
    ctx.setFontSize(26)
    ctx.fillText(String(v), 240, y)
    y += 60
  })

  // 二维码
  let qrPath = ''
  try {
    qrPath = await downloadImage(
      `${ENV.baseURL}/trace/${encodeURIComponent(b.traceCode)}/qrcode?size=600`
    )
  } catch (e) {
    qrPath = ''
  }

  if (qrPath) {
    ctx.drawImage(qrPath, W - 300, 440, 220, 220)
    ctx.setFillStyle('#94a3b8')
    ctx.setFontSize(20)
    ctx.setTextAlign('center')
    ctx.fillText('扫码查看全流程履历', W - 190, 690)
    ctx.setTextAlign('left')
  }

  // 链上存证条
  ctx.setFillStyle('#e8f5ee')
  drawRoundRect(ctx, 40, 924, W - 80, 96, 20)
  ctx.fill()
  ctx.setFillStyle('#1f7a4d')
  ctx.setFontSize(24)
  ctx.fillText('本批次关键节点已写入联盟链存证，数据不可篡改', 72, 968)
  ctx.setFillStyle('#64748b')
  ctx.setFontSize(20)
  const tx = b.chainTxHash || ''
  ctx.fillText(tx ? `tx ${tx.slice(0, 18)}…${tx.slice(-8)}` : '链服务演示模式', 72, 1000)

  // 底部
  ctx.setFillStyle('#b6bec9')
  ctx.setFontSize(20)
  ctx.setTextAlign('center')
  ctx.fillText('FISCO-BCOS 联盟链 · 农安链农产品溯源平台', W / 2, 1090)
  ctx.fillText('扫码或输入溯源码，查看从田间到餐桌的每一步', W / 2, 1126)

  await new Promise((resolve) => ctx.draw(false, resolve))

  // 导出图片
  await new Promise((resolve) => setTimeout(resolve, 260))
  uni.canvasToTempFilePath({
    canvasId: 'posterCanvas',
    width: W,
    height: H,
    destWidth: W * dpr,
    destHeight: H * dpr,
    success: (res) => {
      posterPath.value = res.tempFilePath
      resolve()
    },
    fail: () => {
      toast('海报生成失败，请重试')
      resolve()
    }
  })
}

function previewPoster() {
  if (!posterPath.value) return
  uni.previewImage({ urls: [posterPath.value] })
}

function saveToAlbum() {
  if (!posterPath.value) return
  uni.saveImageToPhotosAlbum({
    filePath: posterPath.value,
    success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
    fail: (err) => {
      if (String(err.errMsg || '').indexOf('auth deny') > -1) {
        uni.showModal({
          title: '需要相册权限',
          content: '请在设置中允许保存图片到相册',
          confirmText: '去设置',
          success: (r) => {
            if (r.confirm) uni.openSetting()
          }
        })
      } else {
        toast('保存失败')
      }
    }
  })
}

onLoad(async (opt) => {
  const id = (opt && opt.id) || ''
  const code = (opt && opt.code) || ''
  setCanvasSize()
  try {
    if (id) {
      const res = await batchDetail(id)
      batch.value = (res.data && res.data.batch) || {}
    } else if (code) {
      const res = await traceQuery(code)
      batch.value = res.data || {}
      if (batch.value) {
        batch.value.batchNo = batch.value.batchNo || ''
      }
    }
  } catch (e) {
    batch.value = {}
  }
  if (batch.value && batch.value.id) {
    await nextTick()
    buildPoster()
  }
})
</script>

<style lang="scss" scoped>
.poster-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 24rpx 32rpx 60rpx;
  box-sizing: border-box;
}
.loading {
  padding: 200rpx 0;
  text-align: center;
  font-size: 26rpx;
  color: #94a3b8;
}
.canvas {
  position: fixed;
  left: -9999rpx;
  top: 0;
}
.preview {
  background: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
  box-shadow: 0 6rpx 24rpx rgba(20, 67, 44, 0.08);
}
.poster-img {
  width: 100%;
  border-radius: 12rpx;
  display: block;
}
.ph {
  height: 700rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ph-icon {
  font-size: 60rpx;
  color: #cbd5e1;
}
.ph-text {
  font-size: 26rpx;
  color: #94a3b8;
  margin-top: 16rpx;
}
.tips {
  padding: 24rpx 8rpx 8rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.tip-line {
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.6;
}
.actions {
  display: flex;
  flex-direction: row;
  gap: 20rpx;
  margin-top: 24rpx;
}
.btn {
  flex: 1;
  height: 88rpx;
  border-radius: 12rpx;
  background: #1f7a4d;
  color: #fff;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.ghost {
  background: #fff;
  color: #1f7a4d;
  border: 1rpx solid #cfe3d8;
}
.btn.disabled {
  opacity: 0.45;
}
</style>
