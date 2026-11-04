<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">溯</div>
        <div class="brand-text">
          <div class="brand-name">农安链溯源</div>
          <div class="brand-sub">FISCO-BCOS TRACE</div>
        </div>
      </div>

      <el-menu
        :default-active="activePath"
        class="menu"
        background-color="transparent"
        text-color="#cfe3d8"
        active-text-color="#ffffff"
        router
        :collapse="false"
      >
        <template v-for="item in visibleMenus" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.meta.icon" /></el-icon>
              <span>{{ item.meta.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              {{ child.meta.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-foot">
        <div class="chain-pill" :class="chainClass">
          <span class="dot"></span>
          <div>
            <div class="chain-title">{{ chainTitle }}</div>
            <div class="chain-sub">{{ chainSub }}</div>
          </div>
        </div>
        <div class="copyright">© 2026 agri-origin</div>
      </div>
    </aside>

    <section class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-root">农产品质量安全溯源</span>
          <el-icon class="sep"><ArrowRight /></el-icon>
          <span class="crumb-current">{{ currentTitle }}</span>
        </div>

        <div class="topbar-right">
          <el-tag v-if="isDemo" type="info" effect="plain" round>演示模式 · 未上链</el-tag>
          <el-tooltip :content="chainTooltip" placement="bottom">
            <div class="chain-chip" :class="chainClass">
              <span class="dot"></span>{{ chainTitle }}
            </div>
          </el-tooltip>

          <el-dropdown trigger="click" @command="onCommand">
            <div class="user">
              <div class="avatar">{{ (store.displayName || 'U').slice(0, 1) }}</div>
              <div class="user-meta">
                <div class="user-name">{{ store.displayName }}</div>
                <div class="user-role">{{ roleLabel }}</div>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </section>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="profileVisible" title="个人信息" width="460px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="姓名">{{ store.user?.realName }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ store.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleLabel }}</el-descriptions-item>
        <el-descriptions-item label="所属村">{{ store.user?.villageName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="所属机构" :span="2">{{ store.user?.orgName || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { menuRoutes } from '@/router'
import { useUserStore } from '@/store/user'
import { chainStatus, changePassword } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const chain = ref({ enabled: false, ready: false, message: '', blockNumber: null })

const activePath = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '首页')

const visibleMenus = computed(() =>
  menuRoutes
    // 无子菜单的项原样保留，避免被补上空 children 后在最后一步被误剔除
    .map((item) =>
      item.children && item.children.length
        ? {
            ...item,
            children: item.children.filter((c) => !c.meta?.perm || store.hasPerm(c.meta.perm))
          }
        : item
    )
    .filter((item) => !item.meta?.perm || store.hasPerm(item.meta.perm))
    // 分组项若子菜单全部无权限则整组隐藏
    .filter((item) => !item.children || item.children.length > 0)
)

const isDemo = computed(() => !chain.value.enabled)
const chainTitle = computed(() => {
  if (!chain.value.enabled) return '演示模式'
  return chain.value.ready ? '链服务正常' : '链服务异常'
})
const chainSub = computed(() => {
  if (!chain.value.enabled) return '存证标记为 SKIPPED'
  return chain.value.blockNumber ? `区块高度 ${chain.value.blockNumber}` : chain.value.message || '—'
})
const chainClass = computed(() => {
  if (!chain.value.enabled) return 'skip'
  return chain.value.ready ? 'ok' : 'fail'
})
const chainTooltip = computed(() => chain.value.message || chainSub.value)

const roleLabel = computed(() => store.user?.roleName || '未分配角色')

const pwdVisible = ref(false)
const profileVisible = ref(false)
const saving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const onCommand = async (command) => {
  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    await store.logout()
    router.replace('/login')
  } else if (command === 'password') {
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
    pwdVisible.value = true
  } else if (command === 'profile') {
    profileVisible.value = true
  }
}

const submitPassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    return ElMessage.warning('请填写完整')
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    return ElMessage.warning('两次输入的新密码不一致')
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    pwdVisible.value = false
    await store.logout()
    router.replace('/login')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await chainStatus()
    chain.value = res.data
  } catch (e) {
    chain.value = { enabled: false, ready: false, message: '无法获取链状态' }
  }
})
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 228px;
  flex: 0 0 228px;
  background: linear-gradient(180deg, #143d2c 0%, #1b5038 55%, #17452f 100%);
  display: flex;
  flex-direction: column;
  position: relative;
}

.sidebar::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 20% 0%, rgba(126, 217, 165, 0.16), transparent 55%);
  pointer-events: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px 18px;
  position: relative;
  z-index: 1;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6fd39b, #2f9e63);
  color: #0d2c1e;
  font-weight: 800;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25);
}

.brand-name {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  color: rgba(207, 227, 216, 0.65);
  font-size: 9px;
  letter-spacing: 1.6px;
  margin-top: 3px;
}

.menu {
  border-right: none;
  padding: 6px 10px;
  overflow-y: auto;
  flex: 1;
  position: relative;
  z-index: 1;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  border-radius: 10px;
  margin-bottom: 4px;
  font-size: 14px;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(111, 211, 155, 0.32), rgba(111, 211, 155, 0.1)) !important;
  box-shadow: inset 3px 0 0 #6fd39b;
  font-weight: 600;
}

.menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 46px !important;
}

.sidebar-foot {
  padding: 14px 16px 16px;
  position: relative;
  z-index: 1;
}

.chain-pill {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.chain-pill .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
}

.chain-pill.ok .dot {
  background: #6fd39b;
  box-shadow: 0 0 0 4px rgba(111, 211, 155, 0.18);
}

.chain-pill.skip .dot {
  background: #b6c2cf;
}

.chain-pill.fail .dot {
  background: #ff8a8a;
}

.chain-title {
  color: #eaf6ef;
  font-size: 12px;
  font-weight: 600;
}

.chain-sub {
  color: rgba(207, 227, 216, 0.6);
  font-size: 10px;
  margin-top: 2px;
  word-break: break-all;
}

.copyright {
  text-align: center;
  color: rgba(207, 227, 216, 0.35);
  font-size: 10px;
  margin-top: 12px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex: 0 0 64px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.crumb-root {
  color: var(--text-secondary);
}

.sep {
  color: #b9c6d3;
  font-size: 12px;
}

.crumb-current {
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chain-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 5px 12px;
}

.chain-chip .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b6c2cf;
}

.chain-chip.ok .dot {
  background: var(--primary);
}

.chain-chip.fail .dot {
  background: var(--danger);
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 10px;
}

.user:hover {
  background: #f5f8f6;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #43b47a, #237a4d);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.user-role {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.caret {
  color: #9aa8b6;
  font-size: 12px;
}

.content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-leave-to {
  opacity: 0;
}
</style>
