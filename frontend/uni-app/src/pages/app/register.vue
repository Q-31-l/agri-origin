<template>
  <!--
    App 专属注册入口：先选身份，再进原注册表单。
    只做"选身份 + 跳转"，真实表单仍在 pages/auth/register，
    这样校验、定位、村庄选择、实名逻辑只有一份，不会因为多一套页面而走偏。
  -->
  <view class="ar">
    <view class="head">
      <view class="head-bg"></view>
      <view class="nav">
        <view class="back" @click="back">
          <app-icon name="chevronRight" color="#9ca3af" :size="40" />
        </view>
        <text class="nav-t">创建账号</text>
        <view class="back ghost"></view>
      </view>
      <view class="head-main">
        <text class="h1">你要以什么身份加入</text>
        <text class="h2">身份决定你能看到哪些批次、能做哪些操作，注册后不能自行更改</text>
      </view>
    </view>

    <view class="body">
      <view class="group">
        <view class="group-head">
          <text class="group-t">自助注册</text>
          <text class="group-s">提交后即可使用</text>
        </view>
        <view
          v-for="r in selfRoles"
          :key="r.code"
          class="role"
          @click="pick(r)"
        >
          <view class="role-ic" :style="{ background: tone(r).bg }">
            <app-icon :name="r.icon" :color="tone(r).color" :size="46" />
          </view>
          <view class="role-main">
            <text class="role-n">{{ r.name }}</text>
            <text class="role-h">{{ r.hint }}</text>
          </view>
          <view class="role-go">
            <app-icon name="chevronRight" color="#9ca3af" :size="32" />
          </view>
        </view>
      </view>

      <view class="group">
        <view class="group-head">
          <text class="group-t">治理身份</text>
          <text class="group-s">需实名 + 系统管理员审核</text>
        </view>
        <view
          v-for="r in reviewRoles"
          :key="r.code"
          class="role"
          @click="pick(r)"
        >
          <view class="role-ic" :style="{ background: tone(r).bg }">
            <app-icon :name="r.icon" :color="tone(r).color" :size="46" />
          </view>
          <view class="role-main">
            <view class="role-n-row">
              <text class="role-n">{{ r.name }}</text>
              <text class="role-badge">待审核</text>
            </view>
            <text class="role-h">{{ r.hint }}</text>
          </view>
          <view class="role-go">
            <app-icon name="chevronRight" color="#9ca3af" :size="32" />
          </view>
        </view>
      </view>

      <view class="foot">
        <text class="foot-t">已经有账号？</text>
        <text class="foot-link" @click="toLogin">直接登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/common/app-icon.vue'
import { REGISTER_ROLES, REVIEW_ROLE_CODES, ROLE_TONE } from '@/config/roles'
import { ROUTES } from '@/config/routes'
import { isAppUI, isPcWidth } from '@/config/platform'

let redirect = ''

const selfRoles = computed(() => REGISTER_ROLES.filter((r) => !REVIEW_ROLE_CODES.includes(r.code)))
const reviewRoles = computed(() => REGISTER_ROLES.filter((r) => REVIEW_ROLE_CODES.includes(r.code)))

onLoad((opt) => {
  redirect = opt && opt.redirect ? decodeURIComponent(opt.redirect) : ''
  // 浏览器直接访问时回原注册页，这一页只服务于壳内 App
  if (!isAppUI()) {
    // #ifdef H5
    uni.reLaunch({ url: isPcWidth() ? ROUTES.PC_DASHBOARD : ROUTES.REGISTER })
    // #endif
  }
})

function tone(r) {
  return ROLE_TONE[r.code] || { bg: '#f1f2f4', color: '#374151' }
}

/** 带上 role 预选身份，redirect 继续往后传，注册成功后一次落到位 */
function pick(r) {
  const qs = []
  qs.push('role=' + encodeURIComponent(r.code))
  if (redirect) qs.push('redirect=' + encodeURIComponent(redirect))
  uni.navigateTo({ url: ROUTES.REGISTER + '?' + qs.join('&') })
}

function toLogin() {
  const q = redirect ? `?redirect=${encodeURIComponent(redirect)}` : ''
  uni.reLaunch({ url: ROUTES.APP_LOGIN + q })
}

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) return uni.navigateBack()
  uni.reLaunch({ url: ROUTES.APP_LOGIN })
}
</script>

<style lang="scss" scoped>
.ar {
  min-height: 100vh;
  background: #f4f6f8;
}

/* ================= 头部 ================= */
.head {
  position: relative;
  padding-bottom: 40rpx;
  background: linear-gradient(158deg, #0b3d26 0%, #14563a 60%, #1f7a4d 100%);
  overflow: hidden;
}
.head-bg {
  position: absolute;
  width: 420rpx;
  height: 420rpx;
  right: -180rpx;
  top: -200rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}
.nav {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 24rpx;
}
.back {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(180deg);
}
.back.ghost {
  opacity: 0;
}
.nav-t {
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
}
.head-main {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  padding: 20rpx 40rpx 0;
}
.h1 {
  font-size: 42rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.35;
}
.h2 {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: rgba(224, 255, 238, 0.62);
  line-height: 1.6;
}

/* ================= 列表 ================= */
.body {
  padding: 32rpx 32rpx 48rpx;
  padding-bottom: calc(48rpx + env(safe-area-inset-bottom));
}
.group {
  margin-bottom: 36rpx;
}
.group-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  margin-bottom: 16rpx;
  padding-left: 8rpx;
}
.group-t {
  font-size: 26rpx;
  font-weight: 600;
  color: $text-1;
}
.group-s {
  margin-left: 14rpx;
  font-size: 20rpx;
  color: $text-3;
}

.role {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 24rpx;
  margin-bottom: 16rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(15, 40, 28, 0.04);
}
.role:active {
  background: #f8fafb;
}
.role-ic {
  width: 92rpx;
  height: 92rpx;
  border-radius: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.role-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 22rpx;
  min-width: 0;
}
.role-n-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.role-n {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.role-badge {
  margin-left: 12rpx;
  font-size: 18rpx;
  color: #b45309;
  background: #fff4e0;
  border-radius: 8rpx;
  padding: 2rpx 10rpx;
}
.role-h {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: $text-3;
  line-height: 1.5;
}
.role-go {
  margin-left: 12rpx;
  flex-shrink: 0;
}

.foot {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin-top: 20rpx;
}
.foot-t {
  font-size: 24rpx;
  color: $text-3;
}
.foot-link {
  font-size: 24rpx;
  color: $brand;
  margin-left: 8rpx;
  font-weight: 600;
}
</style>
