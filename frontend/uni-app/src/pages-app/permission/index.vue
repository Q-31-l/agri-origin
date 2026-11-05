<template>
  <view class="pm-page">
    <view class="head">
      <text class="h-title">权限说明</text>
      <text class="h-sub">以下权限仅在对应功能被使用时才会申请，你可以随时在系统设置中管理</text>
    </view>

    <view class="list">
      <view v-for="p in permissions" :key="p.key" class="item">
        <view class="i-icon" :style="{ background: p.bg, color: p.color }">{{ p.icon }}</view>
        <view class="i-body">
          <text class="i-title">{{ p.title }}</text>
          <text class="i-desc">{{ p.desc }}</text>
        </view>
        <text class="i-state" :class="p.granted ? 'on' : 'off'">
          {{ p.granted ? '已授权' : '未授权' }}
        </text>
      </view>
    </view>

    <view class="note">
      <text class="n-line">· 相机：用于扫描批次溯源二维码。</text>
      <text class="n-line">· 相册：用于上传现场照片，或保存溯源海报。</text>
      <text class="n-line">· 位置：用于在农事记录中标记地块坐标。</text>
      <text class="n-line">· 存储：用于缓存报告文件与图片，减少重复下载。</text>
    </view>

    <view class="actions">
      <view class="btn" @click="openSettings">前往系统设置</view>
    </view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { toast } from '@/utils/ui'

const permissions = ref([
  {
    key: 'camera',
    title: '相机',
    desc: '扫描溯源二维码，快速查询批次履历',
    icon: '⌗',
    bg: '#e8f0fe',
    color: '#2563eb',
    granted: false
  },
  {
    key: 'album',
    title: '相册',
    desc: '上传农事现场照片、保存分享海报',
    icon: '▤',
    bg: '#e8f5ee',
    color: '#1f7a4d',
    granted: false
  },
  {
    key: 'location',
    title: '位置信息',
    desc: '记录农事操作发生的地块坐标',
    icon: '◎',
    bg: '#fef6e0',
    color: '#b45309',
    granted: false
  },
  {
    key: 'storage',
    title: '存储',
    desc: '缓存检测报告与图片文件',
    icon: '⛁',
    bg: '#f1eaff',
    color: '#7c3aed',
    granted: false
  }
])

function refresh() {
  // #ifdef APP-PLUS
  try {
    const st = uni.getAppAuthorizeSetting ? uni.getAppAuthorizeSetting() : null
    if (st) {
      permissions.value = permissions.value.map((p) => {
        if (p.key === 'camera') return { ...p, granted: st.cameraAuthorized === 'authorized' }
        if (p.key === 'album') return { ...p, granted: st.albumAuthorized === 'authorized' }
        if (p.key === 'location') return { ...p, granted: st.locationAuthorized === 'authorized' }
        return p
      })
    }
  } catch (e) {
    /* 忽略 */
  }
  // #endif
}

function openSettings() {
  // #ifdef APP-PLUS
  try {
    if (uni.openAppAuthorizeSetting) {
      uni.openAppAuthorizeSetting({
        fail: () => plus.runtime.openURL('app-settings:')
      })
      return
    }
    plus.runtime.openURL('app-settings:')
  } catch (e) {
    toast('请手动前往系统设置中开启权限')
  }
  // #endif
  // #ifndef APP-PLUS
  toast('请在系统设置中管理应用权限')
  // #endif
}

onShow(refresh)
</script>

<style lang="scss" scoped>
.pm-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}
.head {
  padding: 16rpx 12rpx 28rpx;
}
.h-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #1f2937;
}
.h-sub {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  line-height: 1.6;
  margin-top: 10rpx;
}
.list {
  background: #fff;
  border-radius: 16rpx;
  padding: 0 28rpx;
  margin-bottom: 20rpx;
}
.item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.item:last-child {
  border-bottom: none;
}
.i-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.i-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.i-title {
  font-size: 28rpx;
  color: #1f2937;
}
.i-desc {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 5rpx;
  line-height: 1.5;
}
.i-state {
  font-size: 21rpx;
  border-radius: 6rpx;
  padding: 4rpx 12rpx;
  flex-shrink: 0;
}
.i-state.on {
  color: #16a34a;
  background: #e8f7ee;
}
.i-state.off {
  color: #94a3b8;
  background: #f1f2f4;
}
.note {
  background: #fff;
  border-radius: 16rpx;
  padding: 26rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.n-line {
  font-size: 22rpx;
  color: #64748b;
  line-height: 1.7;
}
.actions {
  margin-top: 28rpx;
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
</style>
