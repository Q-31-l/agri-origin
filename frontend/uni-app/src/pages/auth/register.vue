<template>
  <view class="page" :class="{ pc: wide }">
    <!-- ============ 仅 PC（H5 宽屏）显示的左侧品牌栏 ============ -->
    <!-- #ifdef H5 -->
    <view v-if="wide" class="pane">
      <view class="pane-glow g1"></view>
      <view class="pane-glow g2"></view>
      <view class="pane-grid"></view>

      <view class="pane-top">
        <brand-logo size="md" tone="light" sub="AGRI-ORIGIN · 可信溯源" />
      </view>

      <view class="pane-copy">
        <text class="kicker">自助入驻</text>
        <view class="pane-h1">
          <text class="pane-h1-l">开一个账号</text>
          <text class="pane-h1-l">把你的劳作记在链上</text>
        </view>
        <text class="pane-p">
          农户填报农事与采收，销售商登记流向与核销。每条记录都会生成哈希并写入联盟链，
          谁在什么时候改过，链上都留痕。
        </text>

        <view class="pane-list">
          <view v-for="b in BENEFITS" :key="b.t" class="benefit">
            <view class="benefit-dot"></view>
            <view class="benefit-body">
              <text class="benefit-t">{{ b.t }}</text>
              <text class="benefit-d">{{ b.d }}</text>
            </view>
          </view>
        </view>

        <!-- 注册之后会发生什么：四步小链条，既解释流程，也把左栏下半部的空白填实 -->
        <view class="flow">
          <view v-for="(s, i) in FLOW" :key="s" class="flow-item">
            <view class="flow-dot"></view>
            <text class="flow-t">{{ s }}</text>
            <view v-if="i < FLOW.length - 1" class="flow-line"></view>
          </view>
        </view>
      </view>

      <view class="pane-foot">
        <text class="pane-foot-t">注册即可使用 · 无需审核等待</text>
        <text class="pane-foot-s">检测、物流、监管等身份涉及他人数据处置权，需由管理员实名开通</text>
      </view>
    </view>
    <!-- #endif -->

    <!-- ============ 右侧 / 移动端整页：表单 ============ -->
    <!-- 顶部条必须在 .main 之外：.main 有负 margin（把卡片压到绿色条上），
         放进 .main 里会连顶栏一起被推上去，标题被裁掉一半 -->
    <view v-if="!wide" class="topbar">
      <view class="back" @click="goLogin">
        <!-- 返回箭头用 CSS 画：图标是位图，白色变体需要单独导出，
             而这个箭头只有描边两条线，画出来三端完全一致 -->
        <view class="back-arrow"></view>
      </view>
      <view class="topbar-t">
        <text class="topbar-title">创建账号</text>
        <text class="topbar-sub">农户 / 生产 / 检测 / 物流 / 销售，注册即可用</text>
      </view>
      <view class="back-holder"></view>
    </view>

    <view class="main">      <view class="card">
        <view v-if="wide" class="card-head">
          <text class="card-h">创建账号</text>
          <text class="card-sub">选择身份并填写信息，注册成功直接进入系统</text>
        </view>

        <!-- 身份：溯源链上每一环的录入方都能自助注册；治理类角色必须管理员开通 -->
        <view class="block">
          <text class="block-label">我的身份</text>
          <view class="roles">
            <view
              v-for="r in ROLES"
              :key="r.code || 'locked'"
              class="role"
              :class="{ on: !!r.code && form.roleCode === r.code, off: r.locked }"
              @click="pickRole(r)"
            >
              <view class="role-ic" :class="{ on: !!r.code && form.roleCode === r.code }">
                <app-icon
                  :name="r.icon"
                  :size="wide ? '17px' : 34"
                  :color="form.roleCode === r.code ? '#1f7a4d' : '#8a94a6'"
                />
              </view>
              <text class="role-n">{{ r.name }}</text>
              <view v-if="!!r.code && form.roleCode === r.code" class="role-tick">
                <app-icon name="check" :size="wide ? '12px' : 24" color="#1f7a4d" />
              </view>
            </view>
          </view>

          <!-- 选中的身份能做什么：比在每块卡片里塞小字更清楚，也不用挤成两行 -->
          <view v-if="currentRole.hint" class="role-hint">
            <app-icon name="chain" :size="wide ? '13px' : 26" color="#1f7a4d" />
            <text class="role-hint-t">{{ currentRole.hint }}</text>
          </view>
        </view>

        <!-- 基本信息 -->
        <view class="block">
          <text class="block-label">基本信息</text>
          <view class="fields">
            <view class="field" :class="{ on: focus === 'name' }">
              <text class="label">姓名</text>
              <input
                v-model="form.realName"
                class="input"
                placeholder="填写真实姓名，用于溯源署名"
                placeholder-class="ph"
                maxlength="20"
                @focus="focus = 'name'"
                @blur="focus = ''"
              />
            </view>

            <view class="field" :class="{ on: focus === 'phone' }">
              <text class="label">手机号</text>
              <input
                v-model="form.phone"
                class="input"
                type="number"
                placeholder="选填，便于管理员联系核对"
                placeholder-class="ph"
                maxlength="11"
                @focus="focus = 'phone'"
                @blur="focus = ''"
              />
            </view>

            <!-- 农户与生产主体都要落到村：农户决定能看到哪些批次，
                 生产主体决定能在哪个村登记地块、建批次 -->
            <view v-if="needVillage" class="field" :class="{ on: !!selectedVillage }" @click="openVillage">
              <text class="label">所属村</text>
              <text class="input pick" :class="{ empty: !selectedVillage }">
                {{ selectedVillage ? selectedVillage.villageName : '请选择所在行政村' }}
              </text>
              <app-icon name="chevronRight" :size="wide ? '14px' : 28" color="#b6bec9" />
            </view>

            <!-- 机构类身份：机构名会印在溯源页的责任主体上 -->
            <view v-if="needOrg" class="field" :class="{ on: focus === 'org' }">
              <text class="label">机构</text>
              <input
                v-model="form.orgName"
                class="input"
                :placeholder="orgPlaceholder"
                placeholder-class="ph"
                maxlength="40"
                @focus="focus = 'org'"
                @blur="focus = ''"
              />
            </view>
          </view>
        </view>

        <!-- 登录凭证 -->
        <view class="block">
          <text class="block-label">登录凭证</text>
          <view class="fields">
            <view class="field" :class="{ on: focus === 'u' }">
              <text class="label">账号</text>
              <input
                v-model="form.username"
                class="input"
                placeholder="4–20 位字母、数字或下划线"
                placeholder-class="ph"
                maxlength="20"
                @focus="focus = 'u'"
                @blur="focus = ''"
              />
            </view>

            <view class="field" :class="{ on: focus === 'p' }">
              <text class="label">密码</text>
              <input
                v-model="form.password"
                class="input"
                :password="!showPwd"
                placeholder="至少 6 位，不要与账号相同"
                placeholder-class="ph"
                maxlength="32"
                @focus="focus = 'p'"
                @blur="focus = ''"
              />
              <text class="eye" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</text>
            </view>

            <view class="field" :class="{ on: focus === 'p2' }">
              <text class="label">确认</text>
              <input
                v-model="form.confirm"
                class="input"
                :password="!showPwd"
                placeholder="再次输入密码"
                placeholder-class="ph"
                maxlength="32"
                @focus="focus = 'p2'"
                @blur="focus = ''"
                @confirm="submit"
              />
            </view>
          </view>
        </view>

        <text class="notice">
          注册即表示同意平台将你填写的姓名、所属村（机构）用于溯源存证与展示；手机号仅用于内部核对，不对外公开。
        </text>

        <view class="btn" :class="{ dis: loading || !villageReady }" @click="submit">
          <text v-if="!loading">注册并登录</text>
          <text v-else>正在创建账号…</text>
        </view>

        <view class="alt" @click="goLogin">
          <text class="alt-t">已有账号？</text>
          <text class="alt-l">返回登录</text>
        </view>
      </view>

      <!-- PC 右栏底部：扫码溯源入口仍然可点，与登录页保持一致 -->
      <view v-if="wide" class="trace-entry" @click="toTrace">
        <app-icon name="scan" :size="'16px'" color="#1f7a4d" />
        <text class="trace-t">消费者扫码溯源入口</text>
        <app-icon name="arrowRight" :size="'13px'" color="#1f7a4d" />
      </view>
      <text v-else class="foot">FISCO-BCOS 联盟链 · 全流程上链存证</text>
    </view>

    <!-- 选村面板：移动端从底部升起，PC 居中弹出 -->
    <view v-if="sheetOpen" class="mask" @click="sheetOpen = false">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">选择所属村</text>
          <text class="sheet-close" @click="sheetOpen = false">关闭</text>
        </view>
        <scroll-view scroll-y class="sheet-body">
          <view
            v-for="v in villages"
            :key="v.id"
            class="v-item"
            :class="{ on: v.id === form.villageId }"
            @click="chooseVillage(v)"
          >
            <view class="v-body">
              <text class="v-name">{{ v.villageName }}</text>
              <text v-if="v.regionName" class="v-region">{{ v.regionName }}</text>
            </view>
            <app-icon v-if="v.id === form.villageId" name="check" :size="wide ? '16px' : 32" color="#1f7a4d" />
          </view>
          <view v-if="!villages.length" class="v-empty">
            <text class="v-empty-t">村庄列表加载失败，请检查网络后重试</text>
            <text class="v-empty-a" @click="loadVillages">点此重新加载</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import BrandLogo from '@/components/common/brand-logo.vue'
import AppIcon from '@/components/common/app-icon.vue'
import { useUserStore } from '@/store/user'
import { publicVillages } from '@/api'
import { isPcWidth } from '@/config/platform'
import { ROUTES } from '@/config/routes'
import { toast } from '@/utils/ui'

/**
 * 可自助注册的五个身份，顺序按溯源链走：
 * 农户先记农事 → 生产主体建批次 → 检测 → 物流 → 销售。
 * 最后一块是"治理身份"占位，只做说明、不能选——避免用户以为漏做了。
 */
const ROLES = [
  {
    code: 'FARMER',
    name: '农户',
    icon: 'planting',
    hint: '填报农事与采收，只能看到被指派给自己的批次'
  },
  {
    code: 'PRODUCER',
    name: '生产主体',
    icon: 'farm',
    hint: '登记地块、建立批次，把农户和产品组织起来；需填合作社或家庭农场名称'
  },
  {
    code: 'INSPECTOR',
    name: '检测机构',
    icon: 'quality',
    hint: '上传质检报告并给出结论，决定批次能否流入市场；需填机构名称'
  },
  {
    code: 'LOGISTICS',
    name: '物流企业',
    icon: 'logistics',
    hint: '登记运输交接与冷链温度，运输环节逐条上链；需填企业名称'
  },
  {
    code: 'SELLER',
    name: '销售商',
    icon: 'sales',
    hint: '登记销售与批次核销，售出即完成溯源闭环；需填门店或企业名称'
  },
  {
    code: '',
    name: '村管/监管',
    icon: 'lock',
    locked: true,
    hint: ''
  }
]

const BENEFITS = [
  { t: '一环节一存证', d: '农事、采收、销售逐条上链，哈希与区块号可复核' },
  { t: '数据按身份隔离', d: '农户只看自己参与的批次，销售商只看自己经手的流向' },
  { t: '手机就能用', d: '田间拍照即传，微信小程序、App、浏览器同一套账号' }
]

/** 注册后要走的四步，只在 PC 左栏展示 */
const FLOW = ['注册账号', '加入批次', '记录农事', '链上存证']

const store = useUserStore()

const form = reactive({
  roleCode: 'FARMER',
  realName: '',
  phone: '',
  villageId: null,
  orgName: '',
  username: '',
  password: '',
  confirm: ''
})

const isFarmer = computed(() => form.roleCode === 'FARMER')
const isProducer = computed(() => form.roleCode === 'PRODUCER')
/** 村：农户与生产主体必填 */
const needVillage = computed(() => isFarmer.value || isProducer.value)
/** 机构名：除农户外的四种身份必填 */
const needOrg = computed(() => !isFarmer.value)
const ORG_PLACEHOLDER = {
  PRODUCER: '合作社 / 家庭农场全称',
  INSPECTOR: '检测机构全称',
  LOGISTICS: '物流企业全称',
  SELLER: '门店或企业全称'
}
const ORG_LABEL = {
  PRODUCER: '合作社 / 家庭农场',
  INSPECTOR: '检测机构',
  LOGISTICS: '物流企业',
  SELLER: '销售机构'
}
const orgPlaceholder = computed(() => ORG_PLACEHOLDER[form.roleCode] || '机构全称')
const currentRole = computed(() => ROLES.find((r) => r.code === form.roleCode) || ROLES[0])

const focus = ref('')
const showPwd = ref(false)
const loading = ref(false)
const redirect = ref('')

/* ---------------- 村庄选项 ---------------- */
const villages = ref([])
const sheetOpen = ref(false)
const selectedVillage = computed(() => {
  if (!form.villageId) return null
  return villages.value.find((v) => v.id === form.villageId) || null
})
// 农户必须先选到村：没有村的农户登录后查不到任何批次，属于"注册成功却没法用"
const villageReady = computed(() => !isFarmer.value || !!form.villageId)

async function loadVillages() {
  try {
    const res = await publicVillages()
    villages.value = res.data || []
  } catch (e) {
    /* 网络异常已由 request 提示；面板里留"重新加载"入口 */
  }
}

function pickRole(r) {
  if (r.locked) {
    toast('村管、监管、管理员涉及他人数据处置权，需联系管理员实名开通')
    return
  }
  form.roleCode = r.code
}

function openVillage() {
  if (!villages.value.length) loadVillages()
  sheetOpen.value = true
}

function chooseVillage(v) {
  form.villageId = v.id
  sheetOpen.value = false
}

/* ---------------- 宽屏判定（仅 H5 生效） ---------------- */
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
  loadVillages()
})

/* ---------------- 提交 ---------------- */
function validate() {
  if (!form.realName.trim()) return '请填写真实姓名'
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) return '手机号格式不正确'
  if (needVillage.value && !form.villageId) return '请选择所属村'
  if (needOrg.value && !form.orgName.trim()) return '请填写' + (ORG_LABEL[form.roleCode] || '机构') + '名称'
  if (!/^[A-Za-z0-9_]{4,20}$/.test(form.username)) return '账号需为 4–20 位字母、数字或下划线'
  if (form.password.length < 6) return '密码至少 6 位'
  if (form.password === form.username) return '密码不能与账号相同'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  return ''
}

function afterRegister() {
  if (redirect.value) {
    uni.reLaunch({ url: redirect.value })
    return
  }
  // #ifdef H5
  if (isPcWidth()) return uni.reLaunch({ url: ROUTES.PC_DASHBOARD })
  // #endif
  uni.reLaunch({ url: ROUTES.HOME })
}

async function submit() {
  if (loading.value) return
  const msg = validate()
  if (msg) return toast(msg)
  loading.value = true
  try {
    await store.register({
      roleCode: form.roleCode,
      realName: form.realName.trim(),
      phone: form.phone.trim(),
      villageId: needVillage.value ? form.villageId : null,
      orgName: needOrg.value ? form.orgName.trim() : '',
      username: form.username.trim(),
      password: form.password
    })
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(afterRegister, 400)
  } catch (e) {
    /* request 已按后端 code 提示（账号被占用 / 注册过频等） */
  } finally {
    loading.value = false
  }
}

function goLogin() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: ROUTES.LOGIN })
}

function toTrace() {
  uni.navigateTo({ url: ROUTES.TRACE })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: $bg-page;
  display: flex;
  flex-direction: column;
}

/* ==================== 移动端 ==================== */
.topbar {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: calc(var(--status-bar-height) + 20rpx) 28rpx 92rpx;
  background: $brand-grad;
}
.back {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-arrow {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid #fff;
  border-bottom: 4rpx solid #fff;
  transform: rotate(45deg);
  margin-left: 8rpx;
}
.back-holder {
  width: 64rpx;
}
.topbar-t {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.topbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 2rpx;
}
.topbar-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
}

.main {
  flex: 1;
  padding: 0 32rpx 40rpx;
  margin-top: -64rpx;
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
}
.card {
  background: #fff;
  border-radius: 28rpx;
  padding: 36rpx 32rpx 32rpx;
  box-shadow: 0 12rpx 40rpx rgba(17, 38, 28, 0.1);
}
.card-head {
  margin-bottom: 26px;
}
.card-h {
  display: block;
  font-size: 23px;
  font-weight: 700;
  color: #12291d;
}
.card-sub {
  display: block;
  margin-top: 7px;
  font-size: 12.5px;
  color: #8a9a92;
}

.block + .block {
  margin-top: 32rpx;
}
.block-label {
  display: block;
  font-size: 24rpx;
  color: $text-3;
  letter-spacing: 1rpx;
  margin-bottom: 16rpx;
}

/* 身份卡：六个格子（五个可注册 + 一个治理身份说明），两列刚好三行 */
.roles {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 16rpx;
}
.role {
  position: relative;
  width: calc(50% - 8rpx);
  box-sizing: border-box;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 18rpx 16rpx;
  border-radius: 16rpx;
  border: 1px solid $border;
  background: #fafbfc;
}
.role.on {
  border-color: rgba(31, 122, 77, 0.55);
  background: $brand-light;
}
.role.off {
  border-style: dashed;
  background: #fcfdfc;
}
.role.off .role-n {
  color: $text-4;
  font-weight: 500;
}
.role-ic {
  width: 52rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid $border;
  flex: 0 0 auto;
}
.role-ic.on {
  border-color: rgba(31, 122, 77, 0.35);
}
.role-n {
  flex: 1;
  min-width: 0;
  margin-left: 12rpx;
  font-size: 25rpx;
  font-weight: 600;
  color: $text-1;
  /* 名字必须一行放得下：换行会把格子撑高、三列对不齐 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 勾选角标绝对定位，不占名字的宽度 */
.role-tick {
  position: absolute;
  right: 6rpx;
  top: 6rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 选中身份的能力说明：一行字，比塞进卡片里的小字更好读 */
.role-hint {
  margin-top: 18rpx;
  padding: 16rpx 20rpx;
  border-radius: 14rpx;
  background: #f3f9f5;
  border: 1px solid rgba(31, 122, 77, 0.1);
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}
.role-hint-t {
  flex: 1;
  margin-left: 10rpx;
  font-size: 21rpx;
  line-height: 1.65;
  color: $text-2;
}

/* 表单 */
.fields {
  border-radius: 18rpx;
  border: 1px solid $border;
  overflow: hidden;
  background: #fafbfc;
}
.field {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 96rpx;
  padding: 0 24rpx;
  border-bottom: 1px solid $border;
  background: #fff;
}
.field:last-child {
  border-bottom: none;
}
.field.on {
  background: #fdfffe;
  box-shadow: inset 3rpx 0 0 $brand;
}
.label {
  width: 112rpx;
  font-size: 26rpx;
  color: $text-2;
}
.input {
  flex: 1;
  font-size: 27rpx;
  color: $text-1;
}
.pick {
  color: $text-1;
}
.pick.empty {
  color: $text-4;
}
.ph {
  color: $text-4;
  font-size: 26rpx;
}
.eye {
  font-size: 22rpx;
  color: $brand;
  padding-left: 16rpx;
}

.notice {
  display: block;
  margin: 28rpx 4rpx 0;
  font-size: 20rpx;
  line-height: 1.7;
  color: $text-4;
}
.btn {
  margin-top: 28rpx;
  height: 92rpx;
  border-radius: 46rpx;
  background: $brand-grad;
  color: #fff;
  font-size: 31rpx;
  font-weight: 500;
  letter-spacing: 3rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.dis {
  /* 只是"还差一步"的提示，不用压到发灰——按下去仍会给出具体缺哪一项 */
  opacity: 0.72;
}
.alt {
  margin-top: 24rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
.alt-t {
  font-size: 24rpx;
  color: $text-3;
}
.alt-l {
  font-size: 24rpx;
  color: $brand;
  font-weight: 500;
}
.foot {
  display: block;
  text-align: center;
  margin-top: auto;
  padding-top: 40rpx;
  font-size: 20rpx;
  color: $text-4;
}
.trace-entry {
  display: none;
}

/* ==================== 选村面板（移动端：底部升起） ==================== */
.mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(9, 25, 18, 0.45);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.sheet {
  background: #fff;
  border-radius: 32rpx 32rpx 0 0;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  max-height: 74vh;
  display: flex;
  flex-direction: column;
  animation: sheetUp 0.22s ease;
}
.sheet-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx 32rpx 18rpx;
}
.sheet-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $text-1;
}
.sheet-close {
  font-size: 26rpx;
  color: $text-3;
  padding: 8rpx 12rpx;
}
.sheet-body {
  max-height: 60vh;
}
.v-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 26rpx 32rpx;
  border-bottom: 1px solid $border;
}
.v-item.on {
  background: $brand-light;
}
.v-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.v-name {
  font-size: 28rpx;
  color: $text-1;
}
.v-region {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $text-3;
}
.v-empty {
  padding: 70rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.v-empty-t {
  font-size: 24rpx;
  color: $text-3;
}
.v-empty-a {
  margin-top: 18rpx;
  font-size: 24rpx;
  color: $brand;
}
@keyframes sheetUp {
  from {
    transform: translateY(24%);
    opacity: 0.6;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

/* ==================== 平板竖屏（600–899px） ====================
   这一段宽度下 rpx 会被放大近一倍（750rpx ≈ 768px），手机上刚好的字号会变成巨型字。
   这里改用 px 把版式压回手机比例，并把整页收成一张 480px 的居中卡片——与登录页同一套处理。 */
@media (min-width: 600px) and (max-width: 899px) {
  .page {
    max-width: 480px;
    margin: 0 auto;
    box-shadow: 0 24px 70px -50px rgba(9, 40, 25, 0.5);
    overflow: hidden;
  }
  .topbar {
    padding: 22px 18px 56px;
  }
  .back {
    width: 32px;
    height: 32px;
  }
  .back-holder {
    width: 32px;
  }
  .back-arrow {
    width: 9px;
    height: 9px;
    border-width: 2px;
    margin-left: 4px;
  }
  .topbar-title {
    font-size: 17px;
    letter-spacing: 1px;
  }
  .topbar-sub {
    margin-top: 4px;
    font-size: 11px;
  }
  .main {
    margin-top: -38px;
    padding: 0 18px 24px;
  }
  .card {
    border-radius: 14px;
    padding: 18px 16px 16px;
  }
  .block + .block {
    margin-top: 16px;
  }
  .block-label {
    font-size: 12px;
    margin-bottom: 8px;
  }
  .roles {
    gap: 10px;
  }
  .role {
    width: calc(50% - 5px);
    padding: 9px 9px;
    border-radius: 10px;
  }
  .role-ic {
    width: 28px;
    height: 28px;
    border-radius: 8px;
  }
  .role-n {
    margin-left: 7px;
    font-size: 12.5px;
  }
  .role-hint {
    margin-top: 10px;
    padding: 9px 11px;
    border-radius: 8px;
  }
  .role-hint-t {
    margin-left: 6px;
    font-size: 11px;
    line-height: 1.6;
  }
  .fields {
    border-radius: 10px;
  }
  .field {
    min-height: 48px;
    padding: 0 12px;
  }
  .label {
    width: 56px;
    font-size: 13px;
  }
  .input {
    font-size: 13.5px;
  }
  .ph {
    font-size: 13px;
  }
  .eye {
    font-size: 11.5px;
  }
  .notice {
    margin: 14px 2px 0;
    font-size: 10.5px;
  }
  .btn {
    margin-top: 14px;
    height: 46px;
    border-radius: 23px;
    font-size: 15px;
  }
  .alt {
    margin-top: 12px;
  }
  .alt-t,
  .alt-l {
    font-size: 12.5px;
  }
  .foot {
    padding-top: 20px;
    font-size: 10px;
  }
  .sheet {
    border-radius: 16px 16px 0 0;
  }
  .sheet-head {
    padding: 15px 16px 9px;
  }
  .sheet-title {
    font-size: 15px;
  }
  .sheet-close {
    font-size: 13px;
  }
  .v-item {
    padding: 13px 16px;
  }
  .v-name {
    font-size: 14px;
  }
  .v-region {
    font-size: 11px;
  }
}

/* ==================== PC（H5 宽屏）==================== */
/* 断点与 config/platform.js 的 PC_MIN_WIDTH 保持一致：900px */
@media (min-width: 900px) {
  .page.pc {
    flex-direction: row;
    height: 100vh;
    overflow: hidden;
    background: #f6f8f7;
  }

  /* 左栏 */
  .pane {
    position: relative;
    width: 52%;
    min-width: 460px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: 44px 52px 30px;
    background:
      radial-gradient(700px 500px at 4% -16%, rgba(96, 220, 152, 0.24) 0%, rgba(96, 220, 152, 0) 62%),
      linear-gradient(158deg, #0a3a25 0%, #0f4a30 46%, #11553a 100%);
  }
  .pane-glow {
    position: absolute;
    border-radius: 50%;
    pointer-events: none;
  }
  .pane-glow.g1 {
    width: 560px;
    height: 560px;
    right: -200px;
    top: -220px;
    background: radial-gradient(circle, rgba(178, 255, 214, 0.16) 0%, rgba(178, 255, 214, 0) 66%);
  }
  .pane-glow.g2 {
    width: 480px;
    height: 480px;
    left: -170px;
    bottom: -240px;
    background: radial-gradient(circle, rgba(56, 189, 248, 0.13) 0%, rgba(56, 189, 248, 0) 68%);
  }
  .pane-grid {
    position: absolute;
    inset: 0;
    pointer-events: none;
    background-image:
      linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
    background-size: 56px 56px;
    mask-image: radial-gradient(680px 600px at 34% 42%, #000 0%, transparent 78%);
    -webkit-mask-image: radial-gradient(680px 600px at 34% 42%, #000 0%, transparent 78%);
  }
  .pane-top,
  .pane-copy,
  .pane-foot {
    position: relative;
    z-index: 2;
  }
  .pane-copy {
    margin: auto 0;
    max-width: 430px;
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
  }
  .pane-h1 {
    display: flex;
    flex-direction: column;
  }
  .pane-h1-l {
    font-size: 34px;
    line-height: 1.34;
    font-weight: 700;
    color: #fff;
    letter-spacing: 1px;
    text-shadow: 0 2px 18px rgba(3, 24, 14, 0.35);
  }
  .pane-p {
    display: block;
    margin-top: 16px;
    font-size: 13px;
    line-height: 1.95;
    color: rgba(224, 255, 238, 0.7);
    max-width: 400px;
  }
  .pane-list {
    margin-top: 30px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .benefit {
    display: flex;
    flex-direction: row;
    align-items: flex-start;
  }
  .benefit-dot {
    width: 6px;
    height: 6px;
    margin-top: 6px;
    border-radius: 50%;
    background: #8bf0b5;
    box-shadow: 0 0 10px rgba(139, 240, 181, 0.9);
    flex: 0 0 auto;
  }
  .benefit-body {
    margin-left: 12px;
    display: flex;
    flex-direction: column;
  }
  .benefit-t {
    font-size: 13px;
    font-weight: 600;
    color: #eafff3;
    letter-spacing: 0.4px;
  }
  .benefit-d {
    margin-top: 4px;
    font-size: 11.5px;
    line-height: 1.7;
    color: rgba(224, 255, 238, 0.56);
  }

  /* 四步链：与登录页左栏的"五环节"同一套视觉语言 */
  .flow {
    display: flex;
    flex-direction: row;
    align-items: center;
    margin-top: 26px;
    padding-top: 20px;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
  }
  .flow-item {
    display: flex;
    flex-direction: row;
    align-items: center;
  }
  .flow-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #8bf0b5;
    box-shadow: 0 0 10px rgba(139, 240, 181, 0.6);
    flex: 0 0 auto;
  }
  .flow-t {
    margin-left: 7px;
    font-size: 11.5px;
    color: rgba(234, 255, 243, 0.82);
    letter-spacing: 0.4px;
    white-space: nowrap;
  }
  .flow-line {
    width: 22px;
    height: 1px;
    margin: 0 9px;
    background: linear-gradient(90deg, rgba(139, 240, 181, 0.45), rgba(139, 240, 181, 0.1));
  }
  .pane-foot {
    padding-top: 18px;
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    display: flex;
    flex-direction: column;
  }
  .pane-foot-t {
    font-size: 11.5px;
    color: rgba(234, 255, 243, 0.78);
    letter-spacing: 0.4px;
  }
  .pane-foot-s {
    margin-top: 5px;
    font-size: 11px;
    line-height: 1.6;
    color: rgba(224, 255, 238, 0.42);
  }

  /* 右栏 */
  .main {
    flex: 1;
    min-width: 420px;
    margin-top: 0;
    padding: 36px 40px;
    overflow-y: auto;
    align-items: center;
    /* 用 auto 外边距做居中：内容比视口高时它会自动退化成 0，
       justify-content:center 在溢出时会把顶部顶出可视区、还滚不上去 */
    justify-content: flex-start;
    background:
      radial-gradient(rgba(16, 55, 36, 0.05) 1px, rgba(16, 55, 36, 0) 1px),
      radial-gradient(700px 500px at 90% 2%, rgba(31, 122, 77, 0.09) 0%, rgba(31, 122, 77, 0) 62%),
      #f7f9f8;
    background-size: 22px 22px, auto, auto;
  }
  .card {
    width: 448px;
    margin-top: auto;
    padding: 26px 30px 22px;
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.94);
    border: 1px solid rgba(16, 55, 36, 0.07);
    box-shadow:
      0 1px 0 rgba(255, 255, 255, 0.9) inset,
      0 28px 64px -34px rgba(9, 40, 25, 0.42);
  }
  .card-sub {
    margin-top: 7px;
    margin-bottom: 2px;
  }
  .card-head {
    margin-bottom: 18px;
  }
  .block + .block {
    margin-top: 16px;
  }
  .block-label {
    font-size: 12px;
    margin-bottom: 10px;
    letter-spacing: 0.5px;
  }
  .roles {
    gap: 12px;
  }
  .role {
    /* 三列两行，六块刚好排满，不留空格 */
    width: calc(33.333% - 8px);
    padding: 9px 10px;
    border-radius: 11px;
  }
  .role-ic {
    width: 28px;
    height: 28px;
    border-radius: 9px;
  }
  .role-n {
    margin-left: 8px;
    font-size: 12.5px;
  }
  .role-hint {
    margin-top: 10px;
    padding: 9px 11px;
    border-radius: 9px;
  }
  .role-hint-t {
    margin-left: 6px;
    font-size: 11px;
    line-height: 1.6;
  }
  .fields {
    border-radius: 12px;
  }
  .field {
    min-height: 42px;
    padding: 0 13px;
  }
  .field.on {
    box-shadow: inset 2px 0 0 $brand;
  }
  .label {
    width: 54px;
    font-size: 12.5px;
  }
  .input {
    font-size: 13.5px;
  }
  .ph {
    font-size: 13px;
  }
  .eye {
    font-size: 11.5px;
    padding-left: 8px;
  }
  .notice {
    margin: 14px 2px 0;
    font-size: 10.5px;
    line-height: 1.7;
  }
  .btn {
    margin-top: 18px;
    height: 46px;
    border-radius: 11px;
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 2px;
  }
  .alt {
    margin-top: 16px;
  }
  .alt-t,
  .alt-l {
    font-size: 12.5px;
  }
  .trace-entry {
    display: flex;
    flex-direction: row;
    align-items: center;
    width: 448px;
    margin-top: 14px;
    margin-bottom: auto;
    height: 44px;
    padding: 0 14px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.72);
    border: 1px solid rgba(16, 55, 36, 0.08);
    cursor: pointer;
    transition: background 0.16s ease, transform 0.14s ease;
  }
  .trace-entry:hover {
    background: #fff;
    transform: translateY(-1px);
  }
  .trace-t {
    flex: 1;
    margin-left: 9px;
    font-size: 12.5px;
    color: #5c6f66;
  }

  /* PC 上选村面板改成居中弹窗，从底部升起会显得像移动端跑桌面来了 */
  .mask {
    justify-content: center;
    align-items: center;
  }
  .sheet {
    width: 400px;
    max-height: 70vh;
    border-radius: 16px;
    padding-bottom: 0;
    box-shadow: 0 30px 70px -30px rgba(9, 40, 25, 0.5);
  }
  .sheet-head {
    padding: 18px 20px 10px;
  }
  .sheet-title {
    font-size: 15px;
  }
  .sheet-close {
    font-size: 12.5px;
    padding: 4px 8px;
    cursor: pointer;
  }
  .sheet-body {
    max-height: 50vh;
  }
  .v-item {
    padding: 12px 20px;
    cursor: pointer;
  }
  .v-item:hover {
    background: #f4f9f6;
  }
  .v-name {
    font-size: 13.5px;
  }
  .v-region {
    margin-top: 2px;
    font-size: 11px;
  }
  .v-empty {
    padding: 34px 20px;
  }
  .v-empty-t {
    font-size: 12px;
  }
  .v-empty-a {
    margin-top: 9px;
    font-size: 12px;
    cursor: pointer;
  }
}
</style>
