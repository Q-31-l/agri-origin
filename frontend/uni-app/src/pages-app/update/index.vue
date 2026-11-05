<template>
  <view class="up-page">
    <view class="brand">
      <brand-logo size="lg" tone="dark" stack :sub="`v${version}`" />
    </view>

    <view class="card">
      <view class="row">
        <text class="k">当前版本</text>
        <text class="v">v{{ version }}</text>
      </view>
      <view class="row last">
        <text class="k">发布日期</text>
        <text class="v">{{ releaseDate }}</text>
      </view>
    </view>

    <view class="card">
      <text class="s-title">更新内容</text>
      <text v-for="(c, i) in changelog" :key="i" class="s-line">· {{ c }}</text>
    </view>

    <view class="card status" :class="statusType">
      <text class="st-title">{{ statusTitle }}</text>
      <text class="st-desc">{{ statusDesc }}</text>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: checking }" @click="checkUpdate">
        {{ checking ? '检查中…' : '检查更新' }}
      </view>
      <view v-if="hasUpdate" class="btn ghost" @click="doUpdate">立即更新</view>
    </view>

    <text class="foot">如长时间无法更新，请前往应用市场下载最新版本</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ENV } from '@/config/env'
import { toast } from '@/utils/ui'
import BrandLogo from '@/components/common/brand-logo.vue'

const version = ref(ENV.version || '1.0.0')
const releaseDate = ref('2025-11-05')
const checking = ref(false)
const hasUpdate = ref(false)
const remote = ref(null)

const changelog = [
  '支持批次全流程记录与链上存证查询',
  '新增异常批次熔断与处置闭环',
  '优化扫码溯源与分享海报生成',
  '修复部分机型日期选择异常问题'
]

const statusTitle = computed(() => {
  if (!remote.value) return '尚未检查更新'
  return hasUpdate.value ? `发现新版本 v${remote.value.version}` : '已是最新版本'
})

const statusDesc = computed(() => {
  if (!remote.value) return '点击下方按钮检查是否有可用更新'
  return hasUpdate.value
    ? '建议在 Wi-Fi 环境下完成更新'
    : `当前 v${version.value} 为最新版本，感谢你的使用`
})

const statusType = computed(() => {
  if (!remote.value) return 'idle'
  return hasUpdate.value ? 'warn' : 'ok'
})

/** 读取已安装包的真实版本号（App 端） */
function readLocalVersion() {
  // #ifdef APP-PLUS
  try {
    plus.runtime.getProperty(plus.runtime.appid, (info) => {
      if (info && info.version) version.value = info.version
    })
  } catch (e) {
    /* 忽略 */
  }
  // #endif
}

/** 更新包地址：由打包时配置，未配置则视为无更新 */
const UPDATE_URL = ''

function checkUpdate() {
  if (checking.value) return
  checking.value = true
  setTimeout(() => {
    checking.value = false
    if (!UPDATE_URL) {
      remote.value = { version: version.value }
      hasUpdate.value = false
      toast('已是最新版本')
      return
    }
    remote.value = { version: UPDATE_URL.version || version.value }
    hasUpdate.value = true
  }, 700)
}

function doUpdate() {
  if (!UPDATE_URL) return toast('暂无可用更新包')
  // #ifdef APP-PLUS
  uni.showModal({
    title: '发现新版本',
    content: '是否立即下载并安装？',
    success: (r) => {
      if (!r.confirm) return
      const task = plus.downloader.createDownload(UPDATE_URL, {}, (d, status) => {
        if (status === 200) {
          plus.runtime.install(
            d.filename,
            { force: false },
            () => {
              uni.showToast({ title: '更新完成', icon: 'success' })
              plus.runtime.restart()
            },
            () => toast('安装失败')
          )
        } else {
          toast('下载失败')
        }
      })
      task.start()
    }
  })
  // #endif
}

onLoad(() => {
  readLocalVersion()
})
</script>

<style lang="scss" scoped>
.up-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 40rpx 24rpx 60rpx;
  box-sizing: border-box;
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30rpx 0 44rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 8rpx 28rpx;
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
}
.card.status {
  padding: 24rpx 28rpx;
  gap: 8rpx;
}
.card.status.idle {
  background: #fff;
}
.card.status.ok {
  background: #e8f5ee;
}
.card.status.warn {
  background: #fef6e0;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.row.last {
  border-bottom: none;
}
.k {
  font-size: 26rpx;
  color: #64748b;
}
.v {
  font-size: 26rpx;
  color: #1f2937;
}
.s-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2937;
  padding: 24rpx 0 12rpx;
}
.s-line {
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.8;
}
.card .s-line:last-child {
  padding-bottom: 24rpx;
}
.st-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2937;
}
.st-desc {
  font-size: 22rpx;
  color: #64748b;
  line-height: 1.6;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 12rpx;
}
.btn {
  height: 90rpx;
  border-radius: 14rpx;
  background: #1f7a4d;
  color: #fff;
  font-size: 30rpx;
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
  opacity: 0.5;
}
.foot {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: #b6bec9;
  margin-top: 36rpx;
}
</style>
