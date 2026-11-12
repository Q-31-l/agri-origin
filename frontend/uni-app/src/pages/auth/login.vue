<template>
  <!-- #ifdef H5 -->
  <pc-login
    v-if="wide"
    :loading="loading"
    :accounts="accounts"
    :version="version"
    @submit="submit"
    @trace="toTrace"
    @register="toRegister"
  />
  <!-- #endif -->

  <view v-if="!wide" class="login">
    <view class="hero">
      <view class="brand">
        <brand-logo size="sm" tone="light" sub="AGRI-ORIGIN · 可信溯源" />
      </view>
      <text class="slogan">每一份农产品\n都有可信的来路</text>
      <view class="chips">
        <text class="chip">种植采收</text>
        <text class="chip">质量检测</text>
        <text class="chip">物流销售</text>
      </view>
      <!-- 手机端的氛围层：两道同心弧 + 一枚柔光点，撑住竖屏的留白 -->
      <view class="hero-deco"></view>
      <view class="hero-arc a"></view>
      <view class="hero-arc b"></view>
      <view class="hero-glow"></view>
    </view>

    <view class="form-wrap">
      <view class="card">
        <view class="tabs">
          <text class="tab" :class="{ on: mode === 'account' }" @click="mode = 'account'">账号登录</text>
          <!-- #ifdef MP-WEIXIN -->
          <text class="tab" :class="{ on: mode === 'wx' }" @click="mode = 'wx'">微信登录</text>
          <!-- #endif -->
          <!-- #ifdef APP-PLUS -->
          <text class="tab" :class="{ on: mode === 'wx' }" @click="mode = 'wx'">微信登录</text>
          <!-- #endif -->
        </view>

        <view v-if="mode === 'account'">
          <view class="field">
            <text class="label">账号</text>
            <input v-model="form.username" class="input" placeholder="请输入登录账号" placeholder-class="ph" />
          </view>
          <view class="field">
            <text class="label">密码</text>
            <input v-model="form.password" class="input" password placeholder="请输入密码" placeholder-class="ph" />
          </view>

          <view class="btn" :class="{ disabled: loading }" @click="submit">
            <text v-if="!loading">登 录</text>
            <text v-else>登录中…</text>
          </view>

          <view class="demo">
            <text class="demo-title">演示账号（密码 123456）</text>
            <view class="demo-list">
              <text
                v-for="a in accounts"
                :key="a.username"
                class="demo-tag"
                :class="{ on: form.username === a.username }"
                @click="fill(a)"
              >{{ a.label }}</text>
            </view>
          </view>
        </view>

        <!-- #ifdef MP-WEIXIN -->
        <view v-else class="wx-panel">
          <view class="wx-icon"><app-icon name="wechat" color="#07c160" :size="80" /></view>
          <text class="wx-tip">使用微信一键登录，首次登录需绑定已有账号</text>
          <view class="btn" :class="{ disabled: loading }" @click="submitWx">微信一键登录</view>
          <text class="wx-sub" @click="mode = 'account'">使用账号密码登录</text>
        </view>
        <!-- #endif -->

        <!-- #ifdef APP-PLUS -->
        <view v-else class="wx-panel">
          <view class="wx-icon"><app-icon name="wechat" color="#07c160" :size="80" /></view>
          <text class="wx-tip">使用微信一键登录 App，首次登录需绑定已有账号</text>
          <view class="btn" :class="{ disabled: loading }" @click="submitWx">微信一键登录</view>
          <text class="wx-sub" @click="mode = 'account'">使用账号密码登录</text>
        </view>
        <!-- #endif -->

        <!-- 注册入口：三端都在登录卡底部，位置固定，不随"账号/微信"切换而消失 -->
        <view class="signup" @click="toRegister">
          <text class="signup-t">还没有账号？</text>
          <text class="signup-l">自助注册</text>
          <text class="signup-a">›</text>
        </view>
      </view>

      <view class="consumer" @click="toTrace">
        <app-icon name="scan" color="#1f7a4d" :size="38" class="scan-icon" />
        <text class="consumer-text">消费者扫码溯源入口</text>
        <text class="arrow">›</text>
      </view>

      <!-- 三条平台特性：既是信任背书，也把卡片下方的大片留白填实 -->
      <view class="trust-row">
        <view class="trust-item">
          <app-icon name="chain" color="#1f7a4d" :size="30" />
          <text>链上存证</text>
        </view>
        <view class="trust-dot"></view>
        <view class="trust-item">
          <app-icon name="harvest" color="#1f7a4d" :size="30" />
          <text>一物一码</text>
        </view>
        <view class="trust-dot"></view>
        <view class="trust-item">
          <app-icon name="abnormal" color="#1f7a4d" :size="30" />
          <text>异常可追</text>
        </view>
      </view>

      <text class="footer">FISCO-BCOS 联盟链 · 全流程上链存证</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import BrandLogo from '@/components/common/brand-logo.vue'
// #ifdef H5
import PcLogin from '@/components/auth/pc-login.vue'
// #endif
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '@/config/constants'
import { ROUTES } from '@/config/routes'
import AppIcon from '@/components/common/app-icon.vue'
import { isAppShell, isPcWidth } from '@/config/platform'
import { ENV } from '@/config/env'
import { storage } from '@/utils/storage'
import { toast } from '@/utils/ui'

const store = useUserStore()
const accounts = DEMO_ACCOUNTS
const mode = ref('account')
const loading = ref(false)
const redirect = ref('')
const form = reactive({ username: '', password: '' })
const version = ENV.version

/**
 * 宽屏判定：≥768px 走 PC 专用登录版式（左右分栏），
 * 窄屏仍是手机版（叠层 hero + 卡片），两者共用同一套登录逻辑。
 * 纯自动自适应，随窗口宽度实时切换。
 */
const wide = ref(false)

// #ifdef H5
function syncWide() {
  wide.value = isPcWidth()
}
onMounted(() => {
  syncWide()
  window.addEventListener('resize', syncWide)
})
onUnmounted(() => {
  window.removeEventListener('resize', syncWide)
})
// #endif

onLoad((opt) => {
  redirect.value = opt && opt.redirect ? decodeURIComponent(opt.redirect) : ''
  /*
   * 壳内 App 有自己的登录页。退出登录、权限失效等入口都会 reLaunch 到
   * 通用登录页，在这里统一改道，避免用户突然看到浏览器版登录界面；
   * redirect 原样带过去，登录后仍落回原来要去的地方。
   */
  // #ifdef H5
  if (isAppShell()) {
    const q = redirect.value ? '?redirect=' + encodeURIComponent(redirect.value) : ''
    return uni.reLaunch({ url: ROUTES.APP_LOGIN + q })
  }
  // #endif
})

function fill(a) {
  form.username = a.username
  form.password = DEMO_PASSWORD
}

function afterLogin() {
  if (redirect.value) {
    uni.reLaunch({ url: redirect.value })
    return
  }
  // #ifdef H5
  if (isAppShell()) return uni.reLaunch({ url: ROUTES.APP_HOME })
  if (isPcWidth()) return uni.reLaunch({ url: ROUTES.PC_DASHBOARD })
  // #endif
  uni.reLaunch({ url: ROUTES.HOME })
}

async function submit(creds) {
  if (loading.value) return
  // PC 版由子组件回传账号密码，移动端沿用页面内的 form
  const u = (creds && creds.username) || form.username
  const p = (creds && creds.password) || form.password
  if (!u || !p) return toast('请输入账号和密码')
  loading.value = true
  try {
    await store.login({ username: String(u).trim(), password: p })
    // 记住账号只存用户名，密码一律不落盘
    if (!creds || creds.remember) storage.set('last_user', String(u).trim())
    else storage.remove('last_user')
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(afterLogin, 300)
  } catch (e) {
    /* request 已提示 */
  } finally {
    loading.value = false
  }
}

async function submitWx() {
  if (loading.value) return
  loading.value = true
  try {
    const loginRes = await new Promise((resolve, reject) =>
      uni.login({ provider: 'weixin', success: resolve, fail: reject })
    )
    let data
    // #ifdef MP-WEIXIN
    data = await store.wxLogin(loginRes.code)
    // #endif
    // #ifdef APP-PLUS
    data = await store.wxAppLogin(loginRes)
    // #endif
    if (data && data.needBind) {
      uni.redirectTo({ url: `${ROUTES.WX_BIND}?ticket=${encodeURIComponent(data.bindTicket)}` })
      return
    }
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(afterLogin, 300)
  } catch (e) {
    toast((e && e.message) || '微信登录不可用，请使用账号密码')
    mode.value = 'account'
  } finally {
    loading.value = false
  }
}

function toTrace() {
  uni.navigateTo({ url: ROUTES.TRACE })
}

/** 带上 redirect：注册前想去哪一页，注册成功后直接落回那一页 */
function toRegister() {
  const q = redirect.value ? `?redirect=${encodeURIComponent(redirect.value)}` : ''
  // #ifdef H5
  // 壳内先走"选择身份"页，再进表单
  if (isAppShell()) return uni.navigateTo({ url: ROUTES.APP_REGISTER + q })
  // #endif
  uni.navigateTo({ url: ROUTES.REGISTER + q })
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  background: $bg-page;
  display: flex;
  flex-direction: column;
  /* 启动页淡出后接上登录页淡入，避免"啪"地一下换屏 */
  animation: pageIn 0.26s ease both;
}
@keyframes pageIn {
  from {
    /*
      起始不要用 0：启动页换手时底色是品牌绿，若第一帧全透明会先露出浅色页面底，
      在深绿上就是"啪"地闪一下。0.94 既保住轻微的淡入感，又不会透出底色。
    */
    opacity: 0.94;
    transform: translateY(6rpx);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.hero {
  background: $brand-grad;
  padding: calc(var(--status-bar-height) + 60rpx) 48rpx 120rpx;
  position: relative;
  overflow: hidden;
}
.hero-deco {
  position: absolute;
  right: -80rpx;
  top: -80rpx;
  width: 320rpx;
  height: 320rpx;
  border-radius: 160rpx;
  background: rgba(255, 255, 255, 0.08);
}
/* 同心弧：让竖屏 hero 下半部不空，也呼应 logo 的"草坡"曲线 */
.hero-arc {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
.hero-arc.a {
  left: -180rpx;
  bottom: -320rpx;
  width: 760rpx;
  height: 760rpx;
}
.hero-arc.b {
  left: -60rpx;
  bottom: -260rpx;
  width: 620rpx;
  height: 620rpx;
  border-color: rgba(255, 255, 255, 0.09);
}
.hero-glow {
  position: absolute;
  right: -40rpx;
  bottom: -120rpx;
  width: 420rpx;
  height: 420rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(178, 255, 214, 0.28) 0%, rgba(178, 255, 214, 0) 68%);
  pointer-events: none;
}
.brand {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.slogan {
  display: block;
  margin-top: 48rpx;
  font-size: 46rpx;
  font-weight: 700;
  color: #fff;
  line-height: 1.4;
  white-space: pre-line;
}
.chips {
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  margin-top: 28rpx;
}
.chip {
  padding: 6rpx 20rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.9);
  font-size: 22rpx;
}
.form-wrap {
  flex: 1;
  margin-top: -80rpx;
  padding: 0 40rpx 40rpx;
  /* hero 有 position:relative 会盖住负 margin 的卡片，这里必须提升层级 */
  position: relative;
  z-index: 2;
  /* 让版本号贴到屏幕底部：否则卡片之后会剩一大片空白，页面显得头重脚轻 */
  display: flex;
  flex-direction: column;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx 32rpx;
  box-shadow: 0 12rpx 40rpx rgba(17, 38, 28, 0.1);
}
.tabs {
  display: flex;
  flex-direction: row;
  gap: 40rpx;
  margin-bottom: 24rpx;
}
.tab {
  font-size: 30rpx;
  color: $text-3;
  padding-bottom: 10rpx;
  border-bottom: 4rpx solid transparent;
}
.tab.on {
  color: $text-1;
  font-weight: 600;
  border-bottom-color: $brand;
}
.field {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 100rpx;
  border-bottom: 1px solid $border;
}
.label {
  width: 100rpx;
  font-size: 28rpx;
  color: $text-2;
}
.input {
  flex: 1;
  font-size: 28rpx;
  color: $text-1;
}
.ph {
  color: $text-4;
}
.btn {
  margin-top: 40rpx;
  height: 92rpx;
  border-radius: 46rpx;
  background: $brand-grad;
  color: #fff;
  font-size: 32rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 4rpx;
}
.btn.disabled {
  opacity: 0.6;
}
.demo {
  margin-top: 32rpx;
}
.demo-title {
  font-size: 22rpx;
  color: $text-4;
}
.demo-list {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 14rpx;
}
.demo-tag {
  padding: 8rpx 18rpx;
  border-radius: 24rpx;
  background: #f4f6f8;
  color: $text-2;
  font-size: 22rpx;
}
.demo-tag.on {
  background: $brand-light;
  color: $brand;
}
.wx-panel {
  padding: 30rpx 0 10rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.wx-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
.wx-tip {
  margin: 24rpx 0 8rpx;
  font-size: 24rpx;
  color: $text-3;
  text-align: center;
}
.wx-panel .btn {
  width: 100%;
}
.wx-sub {
  margin-top: 24rpx;
  font-size: 24rpx;
  color: $brand;
}
/* 注册入口：用虚线分隔，视觉上是"登录之外的另一个动作"，不与主按钮抢焦点 */
.signup {
  margin-top: 32rpx;
  padding-top: 26rpx;
  border-top: 1px dashed $border;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
.signup-t {
  font-size: 24rpx;
  color: $text-3;
}
.signup-l {
  margin-left: 6rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: $brand;
}
.signup-a {
  margin-left: 4rpx;
  font-size: 26rpx;
  color: $brand;
}
.consumer {
  margin-top: 28rpx;
  height: 92rpx;
  border-radius: 46rpx;
  background: #fff;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 32rpx;
  box-shadow: $shadow-card;
}
.scan-icon {
  margin-right: 16rpx;
}
.consumer-text {
  flex: 1;
  font-size: 26rpx;
  color: $text-2;
}
.arrow {
  font-size: 34rpx;
  color: $text-4;
}
.footer {
  display: block;
  text-align: center;
  margin-top: auto;
  padding-top: 48rpx;
  font-size: 20rpx;
  color: $text-4;
}

/* 平台特性：横排三点，字号压到最小，只做背书不抢焦点 */
.trust-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  margin-top: 40rpx;
}
.trust-item {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.trust-item text {
  margin-left: 8rpx;
  font-size: 21rpx;
  color: $text-3;
  letter-spacing: 0.5rpx;
}
.trust-dot {
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: $text-4;
  margin: 0 24rpx;
  opacity: 0.6;
}

/* 平板竖屏（600–899px）：rpx 在 768 宽会被放大一倍，这里改用 px 把版式压回手机比例，
   同时把整页收成一张 480px 的居中卡片，不让手机版拉满整屏。 */
@media (min-width: 600px) and (max-width: 899px) {
  .login {
    max-width: 480px;
    margin: 0 auto;
    min-height: 100vh;
    background: $bg-page;
    overflow: hidden;
    box-shadow: 0 24px 70px -50px rgba(9, 40, 25, 0.5);
  }
  /* 卡片是负 margin 压在 hero 上的，hero 再留圆角就会露出两块绿角 */
  .hero {
    border-radius: 0;
    padding: 56px 28px 64px;
  }
  .slogan {
    font-size: 32px;
    margin-top: 24px;
  }
  .chips {
    gap: 8px;
    margin-top: 14px;
  }
  .chip {
    font-size: 12px;
    padding: 4px 10px;
    border-radius: 12px;
  }
  .hero-deco {
    width: 180px;
    height: 180px;
    border-radius: 90px;
    right: -50px;
    top: -50px;
  }
  .hero-arc.a {
    width: 420px;
    height: 420px;
    left: -110px;
    bottom: -190px;
  }
  .hero-arc.b {
    width: 340px;
    height: 340px;
    left: -40px;
    bottom: -150px;
  }
  .form-wrap {
    margin-top: -40px;
    padding: 0 20px 24px;
  }
  .card {
    border-radius: 14px;
    padding: 20px 18px 16px;
  }
  .tab {
    font-size: 15px;
    padding-bottom: 5px;
    border-bottom-width: 2px;
  }
  .tabs {
    gap: 20px;
    margin-bottom: 12px;
  }
  .field {
    height: 52px;
  }
  .label {
    width: 52px;
    font-size: 14px;
  }
  .input {
    font-size: 14px;
  }
  .btn {
    height: 48px;
    border-radius: 24px;
    font-size: 16px;
    letter-spacing: 2px;
    margin-top: 20px;
  }
  .demo {
    margin-top: 16px;
  }
  .demo-title {
    font-size: 11px;
  }
  .demo-list {
    gap: 6px;
    margin-top: 7px;
  }
  .demo-tag {
    font-size: 11px;
    padding: 4px 9px;
    border-radius: 12px;
  }
  .signup {
    margin-top: 16px;
    padding-top: 13px;
  }
  .signup-t,
  .signup-l {
    font-size: 12.5px;
  }
  .signup-a {
    font-size: 14px;
  }
  .consumer {
    height: 48px;
    border-radius: 24px;
    margin-top: 14px;
    padding: 0 16px;
  }
  .consumer-text {
    font-size: 13px;
  }
  .scan-icon {
    font-size: 17px;
    margin-right: 8px;
  }
  .footer {
    margin-top: 24px;
    font-size: 10px;
  }
}
</style>
