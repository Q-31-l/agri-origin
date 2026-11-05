<template>
  <view class="settings">
    <view class="group">
      <view class="row">
        <text class="label">当前平台</text>
        <text class="value">{{ platform }}</text>
      </view>
      <view class="row">
        <text class="label">应用版本</text>
        <text class="value">v{{ version }}</text>
      </view>
      <view class="row">
        <text class="label">运行环境</text>
        <text class="value">{{ envName }}</text>
      </view>
      <view class="row">
        <text class="label">接口地址</text>
        <text class="value mono small">{{ baseURL }}</text>
      </view>
      <view class="row">
        <text class="label">网络状态</text>
        <text class="value" :class="{ bad: offline }">{{ offline ? '已断开' : '正常' }}</text>
      </view>
    </view>

    <view class="group">
      <view class="row action" @click="clearCache">
        <text class="label">清理本地缓存</text>
        <text class="arrow">›</text>
      </view>
      <view class="row action" @click="checkUpdate">
        <text class="label">检查更新</text>
        <text class="arrow">›</text>
      </view>
      <view class="row action" @click="goAbout">
        <text class="label">关于系统</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <text class="foot">农安链溯源 · FISCO-BCOS 联盟链农产品质量安全溯源系统</text>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ENV } from '@/config/env'
import { platformName } from '@/config/platform'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { confirm } from '@/utils/ui'
import { nav } from '@/utils/router'
import { setToken, setUser, setPerms } from '@/utils/auth'
import { ROUTES } from '@/config/routes'

const app = useAppStore()
const user = useUserStore()
const platform = platformName()
const version = ENV.version
const baseURL = ENV.baseURL
const offline = computed(() => app.isOffline)
const envName = computed(() => (ENV.env === 'production' ? '生产环境' : ENV.env === 'staging' ? '预发环境' : '开发环境'))

async function clearCache() {
  const ok = await confirm('将清理除登录态以外的本地缓存，确定继续吗？', '清理缓存')
  if (!ok) return
  const token = user.token
  const cachedUser = user.user
  const cachedPerms = user.permissions
  try {
    uni.clearStorageSync()
  } catch (e) {
    /* ignore */
  }
  if (token) {
    // 恢复登录态，避免清缓存把用户踢下线
    setToken(token)
    setUser(cachedUser)
    setPerms(cachedPerms)
  }
  uni.showToast({ title: '已清理', icon: 'success' })
}

function checkUpdate() {
  // #ifdef APP-PLUS
  nav('/pages-app/update/index')
  return
  // #endif
  uni.showModal({ title: '检查更新', content: `当前已是最新版本 v${version}`, showCancel: false })
}

function goAbout() {
  // #ifdef APP-PLUS
  nav('/pages-app/about/index')
  // #endif
  // #ifndef APP-PLUS
  uni.showModal({
    title: '关于系统',
    content: '农安链溯源 v' + version + '\n基于 FISCO-BCOS 联盟链的农产品质量安全溯源系统',
    showCancel: false
  })
  // #endif
}
</script>

<style lang="scss" scoped>
.settings {
  min-height: 100vh;
  background: $bg-page;
  padding: 24rpx;
}
.group {
  background: #fff;
  border-radius: $radius;
  padding: 0 24rpx;
  margin-bottom: 24rpx;
  box-shadow: $shadow-card;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1px solid $border;
}
.row:last-child {
  border-bottom: none;
}
.label {
  font-size: 28rpx;
  color: $text-1;
  flex-shrink: 0;
}
.value {
  flex: 1;
  text-align: right;
  font-size: 26rpx;
  color: $text-3;
}
.value.small {
  font-size: 22rpx;
}
.value.bad {
  color: $danger;
}
.arrow {
  font-size: 32rpx;
  color: $text-4;
  margin-left: 12rpx;
}
.foot {
  display: block;
  text-align: center;
  font-size: 20rpx;
  color: $text-4;
  margin-top: 40rpx;
  line-height: 1.6;
}
</style>
