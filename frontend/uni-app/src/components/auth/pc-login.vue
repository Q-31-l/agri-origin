<template>
  <view class="pl">
    <!-- ================= 左：品牌画面 ================= -->
    <view class="pl-left">
      <!-- 背景光斑与网格：让深绿不呆板 -->
      <view class="glow g1"></view>
      <view class="glow g2"></view>
      <view class="grid"></view>

      <!-- 线稿插画：梯田等高线 + 破土嫩芽 + 链节点 -->
      <svg class="art" viewBox="0 0 720 720" fill="none" aria-hidden="true">
        <defs>
          <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0" stop-color="#b6f5d0" stop-opacity="0.35" />
            <stop offset="1" stop-color="#b6f5d0" stop-opacity="0" />
          </radialGradient>
          <linearGradient id="stem" x1="360" y1="640" x2="360" y2="300" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#eafff3" stop-opacity="0.15" />
            <stop offset="1" stop-color="#eafff3" stop-opacity="0.6" />
          </linearGradient>
        </defs>

        <circle cx="545" cy="165" r="210" fill="url(#sun)" />

        <!-- 梯田等高线 -->
        <g stroke="#ffffff" stroke-opacity="0.09" stroke-width="1">
          <path d="M-80 512C60 452 210 540 360 500S610 448 800 496" />
          <path d="M-80 552C70 492 215 578 365 538S615 486 800 534" />
          <path d="M-80 592C80 532 220 616 370 576S620 524 800 572" />
          <path d="M-80 632C90 572 225 654 375 614S625 562 800 610" />
          <path d="M-80 672C100 612 230 692 380 652S630 600 800 648" />
        </g>

        <!-- 嫩芽线稿 -->
        <g stroke="url(#stem)" stroke-width="2" stroke-linecap="round">
          <path d="M392 636V486" />
          <path d="M392 512c-52-26-96-88-82-152 62 18 96 76 82 152Z" />
          <path d="M392 480c52-26 96-88 82-152-62 18-96 76-82 152Z" />
        </g>

        <!-- 链上节点：一串由下而上的存证记录 -->
        <g>
          <path d="M470 430 556 356 648 300 700 214" stroke="#ffffff" stroke-opacity="0.22" stroke-width="1" stroke-dasharray="3 6" />
          <circle cx="470" cy="430" r="5" fill="#8bf0b5" fill-opacity="0.9" />
          <circle cx="556" cy="356" r="4" fill="#ffffff" fill-opacity="0.55" />
          <circle cx="648" cy="300" r="4" fill="#ffffff" fill-opacity="0.55" />
          <circle cx="700" cy="214" r="6" fill="#8bf0b5" fill-opacity="0.85" />
        </g>
      </svg>

      <view class="lp-top">
        <brand-logo size="md" tone="light" sub="AGRI-ORIGIN · 可信溯源" />
      </view>

      <view class="lp-copy">
        <text class="kicker">区块链农产品溯源平台</text>
        <view class="h1">
          <text class="h1-l">每一份农产品</text>
          <text class="h1-l">都有可信的来路</text>
        </view>
        <text class="p">从种植、采收、质检到物流销售，五个环节逐条上链存证；消费者扫码即可核验，数据不可篡改、责任可追溯。</text>

        <view class="steps">
          <view v-for="(s, i) in STEPS" :key="s" class="step">
            <view class="dot" :style="{ animationDelay: 260 + i * 90 + 'ms' }"></view>
            <text class="st">{{ s }}</text>
            <view v-if="i < STEPS.length - 1" class="line" :style="{ animationDelay: 300 + i * 90 + 'ms' }"></view>
          </view>
        </view>
      </view>

      <!-- 两枚"链上回执"浮签：补住左栏中下部的空，也让"上链"这件事有画面。
           只写结构性事实（上链网络、存证粒度），不写区块高度/分数——
           装饰性数字会与真实链状态不一致，这个平台不伪造任何存证数据。 -->
      <view class="floats">
        <view class="fchip f1">
          <view class="fdot"></view>
          <text class="ft">上链网络</text>
          <text class="fv">FISCO BCOS</text>
        </view>
        <view class="fchip f2">
          <text class="ft">存证粒度</text>
          <text class="fv">一环节一存证</text>
        </view>
      </view>

      <view class="lp-foot">
        <view class="m">
          <text class="mn">5</text>
          <text class="ml">环节全程上链</text>
        </view>
        <view class="sep"></view>
        <view class="m">
          <text class="mn">SHA-256</text>
          <text class="ml">链下重算校验</text>
        </view>
        <view class="sep"></view>
        <view class="m">
          <text class="mn">5 维</text>
          <text class="ml">可信度评分</text>
        </view>
      </view>
    </view>

    <!-- ================= 右：登录表单 ================= -->
    <view class="pl-right">
      <view class="card">
        <text class="h">欢迎回来</text>
        <text class="sub">登录管理后台，继续你的溯源工作</text>

        <view class="fld" :class="{ on: focus === 'u' }">
          <text class="fl">账号</text>
          <input
            v-model="form.username"
            class="fi"
            placeholder="请输入登录账号"
            placeholder-class="ph"
            @focus="focus = 'u'"
            @blur="focus = ''"
            @confirm="ok"
          />
        </view>

        <view class="fld" :class="{ on: focus === 'p' }">
          <text class="fl">密码</text>
          <input
            v-model="form.password"
            class="fi"
            :password="!show"
            placeholder="请输入密码"
            placeholder-class="ph"
            @focus="focus = 'p'"
            @blur="focus = ''"
            @confirm="ok"
          />
          <text class="eye" @click="show = !show">{{ show ? '隐藏' : '显示' }}</text>
        </view>

        <view class="row">
          <view class="rem" @click="remember = !remember">
            <view class="box" :class="{ on: remember }">
              <text v-if="remember" class="tick">✓</text>
            </view>
            <text class="rem-t">记住账号</text>
          </view>
          <text class="hint">忘记密码请联系管理员</text>
        </view>

        <view class="btn" :class="{ dis: loading }" @click="ok">
          <text v-if="!loading">登 录</text>
          <text v-else>登录中…</text>
        </view>

        <!-- 注册入口：与登录按钮同行级，不能只藏在演示账号下面 -->
        <view class="signup" @click="$emit('register')">
          <text class="signup-t">还没有账号？</text>
          <text class="signup-l">立即注册</text>
          <text class="signup-a">→</text>
        </view>

        <view class="demo">
          <text class="dt">演示账号 · 点击填入（密码 123456）</text>
          <view class="dl">
            <text
              v-for="a in accounts"
              :key="a.username"
              class="dchip"
              :class="{ on: form.username === a.username }"
              @click="fill(a)"
            >{{ a.label }}</text>
          </view>
        </view>
      </view>

      <view class="scan" @click="$emit('trace')">
        <text class="si">⌗</text>
        <text class="st">消费者扫码溯源入口</text>
        <text class="sa">→</text>
      </view>

      <text class="foot">FISCO-BCOS 联盟链 · 全流程上链存证 · v{{ version }}</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import BrandLogo from '@/components/common/brand-logo.vue'
import { storage } from '@/utils/storage'

const props = defineProps({
  loading: { type: Boolean, default: false },
  accounts: { type: Array, default: () => [] },
  version: { type: String, default: '' }
})
const emit = defineEmits(['submit', 'trace', 'register'])

const STEPS = ['种植', '采收', '质检', '物流', '销售']
const KEY = 'last_user'

const form = reactive({ username: '', password: '' })
const focus = ref('')
const show = ref(false)
const remember = ref(true)

onMounted(() => {
  const last = storage.get(KEY)
  if (last) {
    form.username = last
    remember.value = true
  }
})

function fill(a) {
  form.username = a.username
  form.password = '123456'
}

function ok() {
  if (props.loading) return
  emit('submit', {
    username: (form.username || '').trim(),
    password: form.password,
    remember: remember.value
  })
}
</script>

<style lang="scss" scoped>
.pl {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: row;
  background: #f6f8f7;
  overflow: hidden;
}

/* ================= 左栏 ================= */
.pl-left {
  position: relative;
  width: 57%;
  min-width: 520px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 44px 56px 34px;
  background:
    radial-gradient(720px 520px at 6% -14%, rgba(96, 220, 152, 0.24) 0%, rgba(96, 220, 152, 0) 62%),
    linear-gradient(158deg, #0a3a25 0%, #0f4a30 46%, #11553a 100%);
}
.glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.g1 {
  width: 620px;
  height: 620px;
  right: -220px;
  top: -240px;
  background: radial-gradient(circle, rgba(178, 255, 214, 0.16) 0%, rgba(178, 255, 214, 0) 66%);
}
.g2 {
  width: 520px;
  height: 520px;
  left: -180px;
  bottom: -260px;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.14) 0%, rgba(56, 189, 248, 0) 68%);
}
.grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(720px 620px at 30% 40%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(720px 620px at 30% 40%, #000 0%, transparent 78%);
}
/* 插画压在文字下面，只做气氛；贴着右下角，像从田埂里长出来 */
.art {
  position: absolute;
  right: -86px;
  bottom: -150px;
  width: 560px;
  height: 560px;
  opacity: 0.55;
  pointer-events: none;
}

.lp-top,
.lp-copy,
.lp-foot {
  position: relative;
  z-index: 2;
}

.lp-copy {
  margin-top: auto;
  margin-bottom: auto;
  max-width: 470px;
  padding-top: 26px;
}
.kicker {
  display: inline-block;
  font-size: 12px;
  letter-spacing: 2px;
  color: rgba(214, 255, 232, 0.72);
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid rgba(214, 255, 232, 0.22);
  background: rgba(214, 255, 232, 0.07);
  margin-bottom: 20px;
  animation: up 0.5s 0.05s both;
}
.h1 {
  display: flex;
  flex-direction: column;
  animation: up 0.55s 0.12s both;
}
.h1-l {
  font-size: 40px;
  line-height: 1.3;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
  text-shadow: 0 2px 18px rgba(3, 24, 14, 0.35);
}
.p {
  display: block;
  margin-top: 18px;
  font-size: 13.5px;
  line-height: 1.95;
  color: rgba(224, 255, 238, 0.7);
  max-width: 420px;
  animation: up 0.55s 0.18s both;
}

/* 五环节链条 */
.steps {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-top: 30px;
}
.step {
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8bf0b5;
  box-shadow: 0 0 0 4px rgba(139, 240, 181, 0.14), 0 0 12px rgba(139, 240, 181, 0.5);
  animation: pop 0.5s both;
}
.st {
  margin-left: 8px;
  font-size: 12.5px;
  color: rgba(228, 255, 240, 0.86);
  letter-spacing: 0.5px;
}
.line {
  width: 34px;
  height: 1px;
  margin: 0 12px;
  background: linear-gradient(90deg, rgba(139, 240, 181, 0.5), rgba(139, 240, 181, 0.12));
  transform-origin: left center;
  animation: grow 0.45s both;
}

/* 浮动回执 */
.floats {
  position: absolute;
  right: 44px;
  bottom: 232px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  pointer-events: none;
}
.fchip {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 9px;
  padding: 9px 14px;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.085);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18), 0 16px 30px -24px rgba(0, 0, 0, 0.8);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
}
.f1 {
  animation: up 0.5s 0.5s both;
}
.f2 {
  margin-right: 22px;
  animation: up 0.5s 0.62s both;
}
.fdot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8bf0b5;
  box-shadow: 0 0 10px rgba(139, 240, 181, 0.9);
}
.ft {
  font-size: 11.5px;
  color: rgba(226, 255, 240, 0.62);
}
.fv {
  font-size: 11.5px;
  font-weight: 600;
  color: #eafff3;
  letter-spacing: 0.3px;
}

/* 底部三组指标 */
.lp-foot {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 22px;
  padding-top: 22px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  animation: up 0.6s 0.34s both;
}
.m {
  display: flex;
  flex-direction: column;
}
.mn {
  font-size: 17px;
  font-weight: 700;
  color: #eafff3;
  letter-spacing: 0.5px;
}
.ml {
  margin-top: 3px;
  font-size: 11px;
  color: rgba(224, 255, 238, 0.5);
  letter-spacing: 0.4px;
}
.sep {
  width: 1px;
  height: 26px;
  background: rgba(255, 255, 255, 0.12);
}

/* ================= 右栏 ================= */
.pl-right {
  flex: 1;
  min-width: 430px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background:
    radial-gradient(rgba(16, 55, 36, 0.055) 1px, rgba(16, 55, 36, 0) 1px),
    radial-gradient(760px 520px at 88% 4%, rgba(31, 122, 77, 0.09) 0%, rgba(31, 122, 77, 0) 62%),
    radial-gradient(600px 460px at 10% 100%, rgba(31, 122, 77, 0.06) 0%, rgba(31, 122, 77, 0) 66%),
    #f7f9f8;
  background-size: 22px 22px, auto, auto, auto;
}
.card {
  width: 384px;
  padding: 36px 34px 30px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(16, 55, 36, 0.07);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.9) inset,
    0 28px 64px -34px rgba(9, 40, 25, 0.42),
    0 4px 14px -8px rgba(9, 40, 25, 0.12);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  animation: cardIn 0.5s cubic-bezier(0.22, 0.8, 0.3, 1) both;
}
.h {
  display: block;
  font-size: 23px;
  font-weight: 700;
  color: #12291d;
  letter-spacing: 0.5px;
}
.sub {
  display: block;
  margin-top: 7px;
  font-size: 12.5px;
  color: #8a9a92;
  margin-bottom: 26px;
}

.fld {
  position: relative;
  height: 46px;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 14px;
  border-radius: 11px;
  background: #f5f7f6;
  border: 1px solid transparent;
  margin-bottom: 13px;
  transition: background 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease;
}
.fld.on {
  background: #fff;
  border-color: rgba(31, 122, 77, 0.55);
  box-shadow: 0 0 0 3px rgba(31, 122, 77, 0.1);
}
.fl {
  width: 40px;
  font-size: 12.5px;
  color: #6f8179;
}
.fi {
  flex: 1;
  font-size: 13.5px;
  color: #12291d;
  letter-spacing: 0.3px;
}
.ph {
  color: #a9b7b0;
  font-size: 13px;
}
.eye {
  font-size: 11.5px;
  color: #1f7a4d;
  padding-left: 8px;
  cursor: pointer;
  user-select: none;
}

.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 22px;
}
.rem {
  display: flex;
  flex-direction: row;
  align-items: center;
  cursor: pointer;
}
.box {
  width: 15px;
  height: 15px;
  border-radius: 4px;
  border: 1px solid #c8d4ce;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 7px;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.box.on {
  background: #1f7a4d;
  border-color: #1f7a4d;
}
.tick {
  font-size: 10px;
  color: #fff;
  line-height: 1;
}
.rem-t {
  font-size: 12px;
  color: #6f8179;
}
.hint {
  font-size: 11.5px;
  color: #a9b7b0;
}

.btn {
  height: 46px;
  border-radius: 11px;
  background: linear-gradient(135deg, #1b6d45 0%, #22925c 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 10px 22px -12px rgba(21, 104, 66, 0.75);
  transition: transform 0.14s ease, box-shadow 0.18s ease, filter 0.18s ease;
}
.btn:hover {
  filter: brightness(1.05);
  box-shadow: 0 14px 26px -12px rgba(21, 104, 66, 0.8);
}
.btn:active {
  transform: translateY(1px);
}
.btn.dis {
  opacity: 0.62;
  cursor: default;
}

/* 注册入口：压在主按钮下方，用浅色弱化，不与"登录"抢注意力 */
.signup {
  margin-top: 14px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
}
.signup-t {
  font-size: 12px;
  color: #8a9a92;
}
.signup-l {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #1f7a4d;
}
.signup-a {
  margin-left: 4px;
  font-size: 12px;
  color: #1f7a4d;
  transition: transform 0.16s ease;
}
.signup:hover .signup-a {
  transform: translateX(2px);
}

.demo {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed #e2e9e5;
}
.dt {
  display: block;
  font-size: 11px;
  color: #a9b7b0;
  letter-spacing: 0.3px;
}
.dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
  margin-top: 10px;
}
.dchip {
  padding: 6px 0;
  border-radius: 8px;
  background: #f2f5f3;
  border: 1px solid transparent;
  color: #5c6f66;
  font-size: 11.5px;
  text-align: center;
  cursor: pointer;
  transition: all 0.14s ease;
}
.dchip:hover {
  background: #eaf3ee;
  color: #1f7a4d;
}
.dchip.on {
  background: #e7f4ec;
  border-color: rgba(31, 122, 77, 0.35);
  color: #1f7a4d;
}

.scan {
  width: 384px;
  margin-top: 14px;
  height: 46px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(16, 55, 36, 0.08);
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 16px;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease, transform 0.14s ease;
}
.scan:hover {
  background: #fff;
  border-color: rgba(31, 122, 77, 0.3);
  transform: translateY(-1px);
}
.si {
  font-size: 17px;
  color: #1f7a4d;
  margin-right: 10px;
}
.st {
  flex: 1;
  font-size: 12.5px;
  color: #5c6f66;
}
.sa {
  font-size: 13px;
  color: #1f7a4d;
}
.foot {
  margin-top: 26px;
  font-size: 11px;
  color: #b3c0b9;
  letter-spacing: 0.4px;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.995);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes up {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes pop {
  from {
    opacity: 0;
    transform: scale(0.4);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes grow {
  from {
    opacity: 0;
    transform: scaleX(0);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 1180px) {
  .pl-left {
    padding: 34px 38px 26px;
    min-width: 420px;
  }
  .h1-l {
    font-size: 33px;
  }
  .p {
    font-size: 12.5px;
  }
  .art {
    width: 470px;
    height: 470px;
    right: -130px;
    bottom: -110px;
  }
  /* 窄屏时浮签会压到五环节链条，直接收起来，只留插画 */
  .floats {
    display: none;
  }
  .pl-right {
    min-width: 380px;
    padding: 28px;
  }
  .card,
  .scan {
    width: 344px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .kicker,
  .h1,
  .p,
  .lp-foot,
  .card,
  .dot,
  .line {
    animation: none !important;
  }
}
</style>
