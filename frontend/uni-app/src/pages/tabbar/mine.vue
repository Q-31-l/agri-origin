<template>
  <view class="mine">
    <view class="header">
      <app-navbar transparent />
      <view class="user">
        <view class="avatar">{{ avatarText }}</view>
        <view class="col flex1">
          <text class="name">{{ store.displayName || '未登录' }}</text>
          <view class="tags">
            <text class="tag">{{ store.roleName || '-' }}</text>
            <text v-if="store.orgName" class="tag ghost">{{ store.orgName }}</text>
          </view>
        </view>
      </view>
      <view class="numbers">
        <view class="num-item" @click="toBatch">
          <text class="nv">{{ stats.totalBatches || 0 }}</text>
          <text class="nl">参与批次</text>
        </view>
        <view class="num-item">
          <text class="nv">{{ platformLabel }}</text>
          <text class="nl">当前端</text>
        </view>
        <view class="num-item">
          <text class="nv">{{ permCount }}</text>
          <text class="nl">权限点</text>
        </view>
      </view>
    </view>

    <view class="body">
      <view class="menu">
        <view class="menu-item" @click="go(ROUTES.BATCH_LIST)">
          <text class="mi-icon">▢</text>
          <text class="mi-title">批次管理</text>
          <text class="mi-arrow">›</text>
        </view>
        <view class="menu-item" @click="go(ROUTES.PLANTING_LIST)">
          <text class="mi-icon">❀</text>
          <text class="mi-title">农事记录</text>
          <text class="mi-arrow">›</text>
        </view>
        <view class="menu-item" @click="go(ROUTES.QUALITY_LIST)">
          <text class="mi-icon">✓</text>
          <text class="mi-title">质量检测</text>
          <text class="mi-arrow">›</text>
        </view>
        <view class="menu-item" @click="go(ROUTES.CHAIN)">
          <text class="mi-icon">⛓</text>
          <text class="mi-title">链上存证</text>
          <text class="mi-arrow">›</text>
        </view>
      </view>

      <view class="menu">
        <view class="menu-item" @click="go(ROUTES.PASSWORD)">
          <text class="mi-icon">⌘</text>
          <text class="mi-title">修改密码</text>
          <text class="mi-arrow">›</text>
        </view>
        <view class="menu-item" @click="go(ROUTES.SETTINGS)">
          <text class="mi-icon">⚙</text>
          <text class="mi-title">设置</text>
          <text class="mi-arrow">›</text>
        </view>
        <!-- #ifdef MP-WEIXIN -->
        <view class="menu-item" @click="go('/pages-mp/poster/index')">
          <text class="mi-icon">▣</text>
          <text class="mi-title">分享海报</text>
          <text class="mi-arrow">›</text>
        </view>
        <!-- #endif -->
        <!-- #ifdef APP-PLUS -->
        <view class="menu-item" @click="go('/pages-app/update/index')">
          <text class="mi-icon">↑</text>
          <text class="mi-title">检查更新</text>
          <text class="mi-arrow">›</text>
        </view>
        <!-- #endif -->
      </view>

      <view class="version">
        <text class="v-text">{{ appName }} v{{ version }}</text>
        <text class="v-sub">FISCO-BCOS 联盟链 · 全流程溯源</text>
      </view>

      <view class="logout" @click="onLogout">退出登录</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/common/app-navbar.vue'
import { useUserStore } from '@/store/user'
import { statsOverview } from '@/api'
import { nav, relaunch } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import { platformName } from '@/config/platform'
import { ENV } from '@/config/env'
import { confirm } from '@/utils/ui'

const store = useUserStore()
const stats = ref({})
const appName = ENV.appName
const version = ENV.version
const platformLabel = platformName()

const avatarText = computed(() => (store.displayName || 'U').slice(0, 1))
const permCount = computed(() => store.permissions.length)

function go(path) {
  nav(path)
}

function toBatch() {
  uni.switchTab({ url: ROUTES.TAB_BATCH })
}

async function onLogout() {
  const ok = await confirm('退出后需要重新登录，确定退出吗？', '退出登录')
  if (!ok) return
  await store.logout()
  relaunch(ROUTES.LOGIN)
}

onShow(async () => {
  try {
    const res = await statsOverview()
    stats.value = res.data || {}
  } catch (e) {
    /* 部分角色无统计权限，忽略 */
  }
})
</script>

<style lang="scss" scoped>
.mine {
  min-height: 100vh;
  background: $bg-page;
}
.header {
  background: $brand-grad;
  padding-bottom: 80rpx;
}
.user {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 8rpx 32rpx 0;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 56rpx;
  background: rgba(255, 255, 255, 0.2);
  border: 2rpx solid rgba(255, 255, 255, 0.45);
  color: #fff;
  font-size: 46rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}
.name {
  font-size: 38rpx;
  font-weight: 700;
  color: #fff;
}
.tags {
  display: flex;
  flex-direction: row;
  gap: 12rpx;
  margin-top: 10rpx;
}
.tag {
  font-size: 20rpx;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  padding: 4rpx 14rpx;
  border-radius: 18rpx;
}
.tag.ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.4);
}
.numbers {
  margin: 32rpx 24rpx 0;
  background: rgba(255, 255, 255, 0.16);
  border-radius: $radius;
  height: 128rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.num-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.nv {
  font-size: 34rpx;
  font-weight: 700;
  color: #fff;
}
.nl {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4rpx;
}
.body {
  margin-top: -50rpx;
  padding: 0 $gap;
}
.menu {
  background: #fff;
  border-radius: $radius;
  overflow: hidden;
  margin-bottom: $gap;
  box-shadow: $shadow-card;
}
.menu-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 30rpx 24rpx;
  border-bottom: 1px solid $border;
}
.menu-item:last-child {
  border-bottom: none;
}
.mi-icon {
  width: 56rpx;
  font-size: 30rpx;
  color: $brand;
}
.mi-title {
  flex: 1;
  font-size: 28rpx;
  color: $text-1;
}
.mi-arrow {
  font-size: 32rpx;
  color: $text-4;
}
.version {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24rpx 0 32rpx;
}
.v-text {
  font-size: 22rpx;
  color: $text-4;
}
.v-sub {
  font-size: 20rpx;
  color: $text-4;
  margin-top: 6rpx;
}
.logout {
  height: 92rpx;
  border-radius: 46rpx;
  background: #fff;
  color: $danger;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-card;
}
</style>
