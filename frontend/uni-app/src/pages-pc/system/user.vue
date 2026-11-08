<template>
  <pc-shell title="用户与角色">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">用户与角色</text>
          <text class="pc-sub">账号、角色与数据范围（农户仅看本人，村级管理员仅看本村）</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增用户</view>
        </view>
      </view>

      <!-- 待审核注册申请：村级管理员/监管人员提交后，系统管理员需在此放行 -->
      <view v-if="pendingCount > 0" class="pc-onboard">
        <view class="ob-bar"></view>
        <view class="ob-body">
          <text class="ob-title">有 {{ pendingCount }} 个注册申请待审核</text>
          <text class="ob-desc">村级管理员、监管人员的注册申请，需你审核通过后账号才能启用。</text>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.keyword"
            placeholder="搜索用户名 / 姓名 / 手机号"
            @confirm="search"
          />
          <pc-select
            v-model="query.roleCode"
            :options="roleFilterOptions"
            placeholder="全部角色"
            width="150px"
            @change="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-user">用户名</text>
          <text class="pc-th">姓名</text>
          <text class="pc-th">角色</text>
          <text class="pc-th w-org">所属组织</text>
          <text class="pc-th w-phone">手机号</text>
          <text class="pc-th w-scope">数据范围</text>
          <text class="pc-th w-time">手机号后四位</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无用户数据</view>
        <view v-else class="pc-tbody">
          <view v-for="u in list" :key="u.id" class="pc-tr">
            <text class="pc-td w-user strong mono">{{ u.username }}</text>
            <text class="pc-td">{{ u.realName || '-' }}</text>
            <text class="pc-td">
              <text class="role-chip" :class="roleClass(u.roleCode)">{{ u.roleName || u.roleCode }}</text>
            </text>
            <text class="pc-td w-org">{{ u.orgName || u.villageName || u.farmName || '-' }}</text>
            <text class="pc-td w-phone">{{ u.phone || '-' }}</text>
            <text class="pc-td w-scope">{{ scopeText(u.dataScope) }}</text>
            <text class="pc-td w-time">{{ tail4(u.phone) }}</text>
            <view class="pc-td w-act">
              <text v-if="u.auditStatus === 'PENDING'" class="pc-link ok" @click="onAudit(u, true)">通过</text>
              <text v-if="u.auditStatus === 'PENDING'" class="pc-link danger" @click="onAudit(u, false)">拒绝</text>
              <text v-if="canUpdate" class="pc-link" @click="openEdit(u)">编辑</text>
              <text v-if="canUpdate" class="pc-link" @click="onReset(u)">重置密码</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal
        v-model="formVisible"
        :title="editing ? '编辑用户' : '新增用户'"
        width="560px"
        :ok-text="editing ? '保存' : '创建'"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">用户名<text class="req">*</text></text>
              <input
                class="pc-input"
                v-model="form.username"
                :disabled="editing"
                placeholder="登录账号"
              />
            </view>
            <view class="pc-field">
              <text class="pc-label">真实姓名<text class="req">*</text></text>
              <input class="pc-input" v-model="form.realName" placeholder="真实姓名" />
            </view>
            <view class="pc-field">
              <text class="pc-label">角色<text class="req">*</text></text>
              <pc-select v-model="form.roleCode" :options="roleOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">手机号</text>
              <input class="pc-input" v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
            </view>
            <view class="pc-field">
              <text class="pc-label">所属组织</text>
              <input class="pc-input" v-model="form.orgName" placeholder="如：XX 合作社" />
            </view>
            <view class="pc-field">
              <text class="pc-label">所属行政村</text>
              <pc-select v-model="form.villageId" :options="villageOptions" placeholder="可不选" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">所属农场</text>
              <pc-select v-model="form.farmId" :options="farmOptions" placeholder="可不选" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">账号状态</text>
              <pc-select v-model="form.status" :options="statusOptions" width="100%" />
            </view>
            <view v-if="!editing" class="pc-field full">
              <text class="pc-label">初始密码</text>
              <input class="pc-input" v-model="form.password" placeholder="留空则默认 123456" />
            </view>
          </view>
          <text class="pc-hint">角色决定可见菜单与可操作范围，创建后可在列表中随时编辑。</text>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import {
  userPage,
  createUser,
  updateUser,
  resetPassword,
  pendingUsers,
  auditUser,
  villageList,
  farmList
} from '@/api'
import { useUserStore } from '@/store/user'
import { ROLE, ROLE_NAME } from '@/config/constants'
import { confirm, toast } from '@/utils/ui'

const store = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const villages = ref([])
const farms = ref([])

const query = reactive({ page: 1, size: 20, keyword: '', roleCode: '' })

const canCreate = computed(() => store.hasPerm('user:create'))
const canUpdate = computed(() => store.hasPerm('user:update'))

const roleOptions = Object.keys(ROLE).map((k) => ({ value: k, label: ROLE_NAME[k] }))
const roleFilterOptions = [{ value: '', label: '全部角色' }, ...roleOptions]

const villageOptions = computed(() =>
  villages.value.map((v) => ({ value: v.id, label: v.villageName }))
)
const farmOptions = computed(() => farms.value.map((f) => ({ value: f.id, label: f.farmName })))

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' }
]

const SCOPE_TEXT = {
  ALL: '全平台',
  VILLAGE: '本村',
  ORG: '本机构',
  SELF: '仅本人'
}

function tail4(phone) {
  const p = String(phone || '')
  return p.length >= 4 ? '****' + p.slice(-4) : '-'
}

function scopeText(s) {
  return SCOPE_TEXT[s] || s || '—'
}

function roleClass(code) {
  if (code === 'ADMIN') return 'admin'
  if (code === 'REGULATOR') return 'reg'
  if (code === 'VILLAGE_ADMIN') return 'village'
  if (code === 'PRODUCER' || code === 'FARMER') return 'prod'
  return 'other'
}

async function loadBase() {
  if (!villages.value.length) {
    try {
      villages.value = (await villageList()).data || []
    } catch (e) {
      villages.value = []
    }
  }
  if (!farms.value.length) {
    try {
      farms.value = (await farmList()).data || []
    } catch (e) {
      farms.value = []
    }
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword.trim()
    if (query.roleCode) params.roleCode = query.roleCode
    const res = await userPage(params)
    list.value = (res.data && res.data.records) || []
    total.value = (res.data && res.data.total) || 0
  } catch (e) {
    toast(e && e.message ? e.message : '加载失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.keyword = ''
  query.roleCode = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

/* ---------------- 新增 / 编辑 ---------------- */
const formVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)

const form = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  phone: '',
  roleCode: 'FARMER',
  orgName: '',
  villageId: '',
  farmId: '',
  status: 1
})

async function openCreate() {
  await loadBase()
  editing.value = false
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    realName: '',
    phone: '',
    roleCode: 'FARMER',
    orgName: '',
    villageId: '',
    farmId: '',
    status: 1
  })
  formVisible.value = true
}

async function openEdit(u) {
  await loadBase()
  editing.value = true
  Object.assign(form, {
    id: u.id,
    username: u.username,
    password: '',
    realName: u.realName || '',
    phone: u.phone || '',
    roleCode: u.roleCode || 'FARMER',
    orgName: u.orgName || '',
    villageId: u.villageId || '',
    farmId: u.farmId || '',
    status: u.status === 0 ? 0 : 1
  })
  formVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.username.trim()) return toast('请填写用户名')
  if (!form.realName.trim()) return toast('请填写真实姓名')
  if (!form.roleCode) return toast('请选择角色')

  const payload = {
    username: form.username.trim(),
    realName: form.realName.trim(),
    phone: form.phone,
    roleCode: form.roleCode,
    orgName: form.orgName,
    status: form.status
  }
  if (form.villageId) payload.villageId = form.villageId
  if (form.farmId) payload.farmId = form.farmId
  if (!editing.value && form.password) payload.password = form.password

  submitting.value = true
  try {
    if (editing.value) await updateUser(form.id, payload)
    else await createUser(payload)
    uni.showToast({ title: editing.value ? '已保存' : '创建成功', icon: 'success' })
    formVisible.value = false
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

async function onReset(u) {
  const ok = await confirm(`确定将「${u.username}」的密码重置为 123456 吗？`, '重置密码')
  if (!ok) return
  try {
    await resetPassword(u.id, '123456')
    uni.showToast({ title: '已重置为 123456', icon: 'success' })
  } catch (e) {
    /* 已提示 */
  }
}

/* ---------------- 注册审核 ---------------- */
const pendingCount = ref(0)

async function loadPending() {
  try {
    const res = await pendingUsers()
    pendingCount.value = (res.data || []).length
  } catch (e) {
    pendingCount.value = 0
  }
}

async function onAudit(u, approve) {
  const ok = await confirm(
    `确定${approve ? '通过' : '拒绝'}「${u.realName || u.username}」（${u.roleName || u.roleCode}）的注册申请吗？`,
    '注册审核'
  )
  if (!ok) return
  try {
    await auditUser(u.id, approve)
    uni.showToast({ title: approve ? '已通过' : '已拒绝', icon: 'success' })
    load()
    loadPending()
  } catch (e) {
    /* 已提示 */
  }
}

onShow(() => {
  load()
  loadBase()
  loadPending()
})
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-user { flex: 1.2; }
.w-org { flex: 1.5; }
.w-phone { flex: 1.1; }
.w-scope { flex: 0.9; }
.w-time { flex: 1.3; }
.w-act { flex: 1.2; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
.role-chip {
  font-size: 10.5px;
  border-radius: 3px;
  padding: 1px 6px;
}
.role-chip.admin {
  color: #7c3aed;
  background: #f1eaff;
}
.role-chip.reg {
  color: #2563eb;
  background: #e8f0fe;
}
.role-chip.village {
  color: #0891b2;
  background: #e0f7fa;
}
.role-chip.prod {
  color: #1f7a4d;
  background: #e8f5ee;
}
.role-chip.other {
  color: #64748b;
  background: #f1f2f4;
}
.st {
  font-size: 10.5px;
  border-radius: 3px;
  padding: 1px 6px;
}
.st.on {
  color: #16a34a;
  background: #e8f7ee;
}
.st.off {
  color: #6b7280;
  background: #f1f2f4;
}
/* 待审核提示条 */
.pc-onboard {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 14px;
  border-radius: 9px;
  background: linear-gradient(90deg, #fdf6ec 0%, #fffdfa 70%);
  border: 1px solid rgba(183, 121, 31, 0.2);
}
.ob-bar {
  width: 3px;
  height: 28px;
  border-radius: 2px;
  background: #b7791f;
  flex: 0 0 auto;
}
.ob-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.ob-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #8a5a00;
}
.ob-desc {
  margin-top: 2px;
  font-size: 11px;
  color: #a07b4b;
}
.pc-link.ok {
  color: #16a34a;
}
.pc-link.danger {
  color: #d93026;
}
</style>
