<template>
  <view class="mine" :class="{ 'as-app': appShell }">
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
        <view class="num-item" @click="go(ROUTES.CHAIN)">
          <text class="nv">{{ stats.chainSuccess || 0 }}</text>
          <text class="nl">链上存证</text>
        </view>
        <view class="num-item">
          <text class="nv">{{ qualifiedText }}</text>
          <text class="nl">质检合格率</text>
        </view>
      </view>
    </view>

    <view class="body">
      <!--
        菜单按权限裁剪：农户看不到"质量检测"，检测机构看不到"农事记录"，
        与 PC 侧栏同一套 perm，避免"手机上点得进去、电脑上没这个入口"的错位。
        某组过滤后为空就整组不渲染。
      -->
      <view v-for="(g, gi) in menuGroups" :key="gi" class="menu">
        <view v-for="m in g" :key="m.title" class="menu-item" @click="go(m.path)">
          <app-icon :name="m.icon" color="#374151" :size="40" class="mi-icon" />
          <text class="mi-title">{{ m.title }}</text>
          <text class="mi-arrow">›</text>
        </view>
      </view>

      <view class="menu">
        <!-- #ifdef MP-WEIXIN -->
        <view class="menu-item" @click="go('/pages-mp/poster/index')">
          <app-icon name="image" color="#374151" :size="40" class="mi-icon" />
          <text class="mi-title">分享海报</text>
          <text class="mi-arrow">›</text>
        </view>
        <!-- #endif -->
        <!-- #ifdef APP-PLUS -->
        <view class="menu-item" @click="go('/pages-app/update/index')">
          <app-icon name="refresh" color="#374151" :size="40" class="mi-icon" />
          <text class="mi-title">检查更新</text>
          <text class="mi-arrow">›</text>
        </view>
        <!-- #endif -->
        <view class="menu-item" @click="go(ROUTES.PASSWORD)">
          <app-icon name="lock" color="#374151" :size="40" class="mi-icon" />
          <text class="mi-title">修改密码</text>
          <text class="mi-arrow">›</text>
        </view>
        <view class="menu-item" @click="go(ROUTES.SETTINGS)">
          <app-icon name="settings" color="#374151" :size="40" class="mi-icon" />
          <text class="mi-title">设置</text>
          <text class="mi-arrow">›</text>
        </view>
      </view>

      <view class="version">
        <text class="v-text">{{ appName }} v{{ version }}</text>
        <text class="v-sub">FISCO-BCOS 联盟链 · 全流程溯源</text>
      </view>

      <view class="logout" @click="onLogout">退出登录</view>
    </view>

    <!-- 壳内与首页共用同一块 Dock，系统 tabBar 由全局样式隐藏 -->
    <app-dock v-if="appShell" active="mine" />
  </view>

</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppNavbar from '@/components/common/app-navbar.vue'
import AppIcon from '@/components/common/app-icon.vue'
import { useUserStore } from '@/store/user'
import { statsOverview } from '@/api'
import { nav, relaunch } from '@/utils/router'
import { ROUTES } from '@/config/routes'
import AppDock from '@/components/app/app-dock.vue'
import { isAppShell } from '@/config/platform'
import { platformName } from '@/config/platform'
import { ENV } from '@/config/env'
import { confirm } from '@/utils/ui'

/** 壳内：改用自绘 Dock，系统 tabBar 已在全局样式里隐藏 */
const appShell = isAppShell()

const store = useUserStore()
const stats = ref({})
const appName = ENV.appName
const version = ENV.version

const avatarText = computed(() => (store.displayName || 'U').slice(0, 1))

/** 合格率：没有报告时显示占位符，而不是刺眼的 0% */
const qualifiedText = computed(() => {
  const v = stats.value.qualifiedRate
  if (v === null || v === undefined) return '—'
  return Number(v).toFixed(0) + '%'
})

/**
 * 业务入口按权限裁剪，与 PC 侧栏共用同一批 perm 标识。
 * 例如农户只有 planting/harvest 权限，就不该在"我的"里看到"质量检测"。
 */
const BUSINESS_MENUS = [
  { title: '批次管理', icon: 'batch', path: ROUTES.BATCH_LIST, perm: 'batch:list' },
  { title: '农事记录', icon: 'planting', path: ROUTES.PLANTING_LIST, perm: 'planting:list' },
  { title: '质量检测', icon: 'quality', path: ROUTES.QUALITY_LIST, perm: 'quality:list' },
  { title: '链上存证', icon: 'chain', path: ROUTES.CHAIN, perm: 'chain:list' }
]

const menuGroups = computed(() => {
  const list = BUSINESS_MENUS.filter((m) => !m.perm || store.hasPerm(m.perm))
  return list.length ? [list] : []
})

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
  margin-right: 6rpx;
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
