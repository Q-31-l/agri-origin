<template>
  <!--
    App 专属登录页。
    与浏览器版（pages/auth/login）的区别：没有系统标题栏、没有左右分栏，
    整屏是品牌深绿背景 + 底部上浮的白色操作面板（sheet），
    演示账号收进折叠区，登录成功后直接落在 App 首页。
    登录逻辑完全复用 store.login，账号体系与 H5 / 小程序一致。
  -->
  <view class="al">
    <!-- 品牌区 -->
    <view class="stage">
      <view class="glow g1"></view>
      <view class="glow g2"></view>
      <view class="arc a"></view>
      <view class="arc b"></view>

      <view class="stage-top">
        <image class="logo" src="/static/logo/mark.png" mode="aspectFit" />
        <text class="brand">农安链溯源</text>
        <text class="brand-en">AGRI-ORIGIN · TRACEABLE</text>
      </view>

      <view class="stage-mid">
        <text class="slogan">每一份农产品</text>
        <text class="slogan">都有可信的来路</text>
        <view class="chips">
          <text class="chip">种植采收</text>
          <text class="chip">质量检测</text>
          <text class="chip">物流销售</text>
        </view>
      </view>
    </view>

    <!-- 操作面板 -->
    <view class="sheet">
      <view class="sheet-head">
        <text class="title">欢迎回来</text>
        <text class="desc">登录后可继续处理你的批次与待办</text>
      </view>

      <view class="field">
        <text class="label">账号</text>
        <view class="input-box" :class="{ focus: focusAt === 'u' }">
          <input
            v-model="form.username"
            class="input"
            type="text"
            :adjust-position="true"
            placeholder="手机号 / 账号"
            placeholder-class="ph"
            @focus="focusAt = 'u'"
            @blur="focusAt = ''"
          />
          <text v-if="form.username" class="clear" @click="form.username = ''">×</text>
        </view>
      </view>

      <view class="field">
        <text class="label">密码</text>
        <view class="input-box" :class="{ focus: focusAt === 'p' }">
          <input
            v-model="form.password"
            class="input"
            :password="!showPwd"
            :adjust-position="true"
            placeholder="请输入登录密码"
            placeholder-class="ph"
            @focus="focusAt = 'p'"
            @blur="focusAt = ''"
            @confirm="submit"
          />
          <text class="eye" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</text>
        </view>
      </view>

      <view class="btn" :class="{ off: loading }" @click="submit">
        <text class="btn-t">{{ loading ? '登录中…' : '登 录' }}</text>
      </view>

      <view class="links">
        <text class="link" @click="toRegister">注册新账号</text>
        <text class="dot">·</text>
        <text class="link" @click="toTrace">不登录，先查溯源</text>
      </view>

      <!-- 演示账号折叠：默认收起，避免首屏太"说明文档" -->
      <view class="demo">
        <view class="demo-head" @click="showDemo = !showDemo">
          <text class="demo-t">演示账号</text>
          <view class="demo-arrow" :class="{ open: showDemo }">
            <app-icon name="chevronRight" color="#9ca3af" :size="28" />
          </view>
        </view>
        <view v-if="showDemo" class="demo-list">
          <view v-for="a in accounts" :key="a.username" class="demo-item" @click="fill(a)">
            <text class="demo-name">{{ a.label }}</text>
            <text class="demo-user">{{ a.username }}</text>
          </view>
          <text class="demo-tip">统一密码 {{ demoPwd }}，点任意一项自动填入</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '@/config/constants'
import { ROUTES } from '@/config/routes'
import { storage } from '@/utils/storage'
import { toast } from '@/utils/ui'
import AppIcon from '@/components/common/app-icon.vue'
import { isAppShell, isPcWidth } from '@/config/platform'
import { getToken } from '@/utils/auth'

const store = useUserStore()
const accounts = DEMO_ACCOUNTS
const demoPwd = DEMO_PASSWORD

const loading = ref(false)
const showPwd = ref(false)
const showDemo = ref(false)
const focusAt = ref('')
const redirect = ref('')
const form = reactive({ username: '', password: '' })

onLoad((opt) => {
  redirect.value = opt && opt.redirect ? decodeURIComponent(opt.redirect) : ''
  // 记住上次登录的账号（只存用户名，密码不落盘）
  const last = storage.get('last_user')
  if (last) form.username = last

  // 直接访问这一页的浏览器用户送回浏览器版登录，避免出现"半成品 App 页"
  if (!isAppShell()) {
    // #ifdef H5
    uni.reLaunch({ url: isPcWidth() ? ROUTES.PC_DASHBOARD : ROUTES.LOGIN })
    // #endif
    return
  }
  // 已登录直接进首页，不让人再登一次
  if (getToken()) uni.reLaunch({ url: ROUTES.APP_HOME })
})

function fill(a) {
  form.username = a.username
  form.password = DEMO_PASSWORD
  showDemo.value = false
}

async function submit() {
  if (loading.value) return
  const u = (form.username || '').trim()
  const p = form.password || ''
  if (!u || !p) return toast('请输入账号和密码')
  loading.value = true
  try {
    await store.login({ username: u, password: p })
    storage.set('last_user', u)
    uni.showToast({ title: '登录成功', icon: 'success' })
    const target = redirect.value || ROUTES.APP_HOME
    setTimeout(() => uni.reLaunch({ url: target }), 300)
  } catch (e) {
    /* 请求层已提示，这里不重复弹 */
  } finally {
    loading.value = false
  }
}

function toRegister() {
  const q = redirect.value ? `?redirect=${encodeURIComponent(redirect.value)}` : ''
  uni.navigateTo({ url: ROUTES.APP_REGISTER + q })
}

function toTrace() {
  uni.navigateTo({ url: ROUTES.TRACE })
}
</script>

<style lang="scss" scoped>
.al {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(160deg, #072b1c 0%, #0c3f29 54%, #14563a 100%);
}

/* ================= 品牌区 ================= */
.stage {
  position: relative;
  flex: 1;
  min-height: 46vh;
  padding: 96rpx 48rpx 0;
  overflow: hidden;
}
.glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.g1 {
  width: 620rpx;
  height: 620rpx;
  right: -240rpx;
  top: -220rpx;
  background: radial-gradient(circle, rgba(96, 220, 152, 0.2) 0%, rgba(96, 220, 152, 0) 68%);
}
.g2 {
  width: 420rpx;
  height: 420rpx;
  left: -180rpx;
  bottom: -160rpx;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.14) 0%, rgba(56, 189, 248, 0) 70%);
}
.arc {
  position: absolute;
  left: 50%;
  top: 120rpx;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.06);
  transform: translateX(-50%);
  pointer-events: none;
}
.arc.a {
  width: 520rpx;
  height: 520rpx;
}
.arc.b {
  width: 700rpx;
  height: 700rpx;
  border-color: rgba(255, 255, 255, 0.035);
}

.stage-top {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.logo {
  width: 108rpx;
  height: 108rpx;
  filter: drop-shadow(0 12rpx 28rpx rgba(2, 22, 13, 0.4));
}
.brand {
  margin-top: 28rpx;
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}
.brand-en {
  margin-top: 10rpx;
  font-size: 18rpx;
  letter-spacing: 4rpx;
  color: rgba(224, 255, 238, 0.42);
}

.stage-mid {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  margin-top: 64rpx;
}
.slogan {
  font-size: 34rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.92);
  line-height: 1.6;
  letter-spacing: 3rpx;
}
.chips {
  display: flex;
  flex-direction: row;
  margin-top: 28rpx;
}
.chip {
  font-size: 20rpx;
  color: rgba(233, 255, 244, 0.72);
  border: 1px solid rgba(178, 255, 214, 0.24);
  border-radius: 28rpx;
  padding: 6rpx 20rpx;
  margin-right: 14rpx;
}

/* ================= 面板 ================= */
.sheet {
  position: relative;
  z-index: 3;
  background: #ffffff;
  border-radius: 44rpx 44rpx 0 0;
  /* 水平内边距单独给：calc 里嵌 constant() 会让整条 padding 声明被浏览器丢弃 */
  padding: 44rpx 48rpx 40rpx;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -12rpx 40rpx rgba(2, 22, 13, 0.18);
}
.sheet-head {
  display: flex;
  flex-direction: column;
}
.title {
  font-size: 42rpx;
  font-weight: 700;
  color: $text-1;
}
.desc {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: $text-3;
}

.field {
  margin-top: 32rpx;
}
.label {
  font-size: 22rpx;
  color: $text-3;
  margin-left: 8rpx;
}
.input-box {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 12rpx;
  height: 100rpx;
  padding: 0 28rpx;
  border-radius: 28rpx;
  background: #f5f7f8;
  border: 1px solid #eef2f4;
}
.input-box.focus {
  background: #ffffff;
  border-color: rgba(31, 122, 77, 0.45);
  box-shadow: 0 0 0 4rpx rgba(31, 122, 77, 0.08);
}
.input {
  flex: 1;
  height: 100rpx;
  font-size: 30rpx;
  color: $text-1;
}
.ph {
  color: #b6bec9;
  font-size: 28rpx;
}
.clear,
.eye {
  font-size: 22rpx;
  color: $text-3;
  padding-left: 16rpx;
}
.clear {
  font-size: 32rpx;
  color: #c7cdd6;
}

.btn {
  margin-top: 44rpx;
  height: 100rpx;
  border-radius: 50rpx;
  background: linear-gradient(135deg, #1f7a4d 0%, #2f9e63 100%);
  box-shadow: 0 14rpx 30rpx rgba(31, 122, 77, 0.26);
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn:active {
  transform: translateY(1rpx);
  box-shadow: 0 8rpx 18rpx rgba(31, 122, 77, 0.22);
}
.btn.off {
  opacity: 0.7;
}
.btn-t {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 6rpx;
}

.links {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin-top: 32rpx;
}
.link {
  font-size: 24rpx;
  color: $brand;
}
.dot {
  font-size: 24rpx;
  color: $text-4;
  margin: 0 16rpx;
}

.demo {
  margin-top: 36rpx;
  border-top: 1px solid #f0f3f5;
  padding-top: 24rpx;
}
.demo-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.demo-t {
  font-size: 24rpx;
  color: $text-2;
}
.demo-arrow {
  width: 28rpx;
  height: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: rotate(90deg);
  transition: transform 0.18s ease;
}
.demo-arrow.open {
  transform: rotate(-90deg);
}
.demo-list {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 18rpx;
}
.demo-item {
  width: 50%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 18rpx 14rpx 0;
}
.demo-name {
  font-size: 22rpx;
  color: $text-2;
}
.demo-user {
  font-size: 20rpx;
  color: $text-3;
}
.demo-tip {
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $text-4;
}
</style>
