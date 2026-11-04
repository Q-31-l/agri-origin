<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">用户与角色</h1>
        <div class="page-desc">RBAC 角色权限体系；村级管理员仅可管理本村农户账号</div>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>

    <div class="card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="账号 / 姓名 / 手机号" clearable :prefix-icon="Search" @keyup.enter="reload" />
        <el-select v-model="query.roleCode" placeholder="角色" clearable style="width: 170px">
          <el-option v-for="r in roles" :key="r.code" :label="r.label" :value="r.code" />
        </el-select>
        <el-button type="primary" plain @click="reload">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="所属机构" min-width="200" show-overflow-tooltip />
        <el-table-column prop="villageName" label="所属村" width="110" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最近登录" width="165" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canReset" link type="warning" @click="doReset(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          background
          @current-change="load"
          @size-change="reload"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="登录账号" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode" style="width: 100%" :disabled="store.roleCode === 'VILLAGE_ADMIN'">
            <el-option v-for="r in roles" :key="r.code" :label="r.label" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属机构">
          <el-input v-model="form.orgName" placeholder="检测机构 / 物流企业 / 销售商等" />
        </el-form-item>
        <el-form-item label="所属村">
          <el-select v-model="form.villageId" clearable style="width: 100%" :disabled="store.roleCode === 'VILLAGE_ADMIN'">
            <el-option v-for="v in villages" :key="v.id" :label="v.villageName" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="form.password" placeholder="留空则默认 123456" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createUser, resetPassword, updateUser, userPage, villageList } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const canCreate = computed(() => store.hasPerm('user:create'))
const canReset = computed(() => store.hasPerm('user:reset'))

const roles = [
  { code: 'ADMIN', label: '系统管理员' },
  { code: 'REGULATOR', label: '监管人员' },
  { code: 'PRODUCER', label: '生产主体' },
  { code: 'VILLAGE_ADMIN', label: '村级管理员' },
  { code: 'FARMER', label: '农户' },
  { code: 'INSPECTOR', label: '检测机构' },
  { code: 'LOGISTICS', label: '物流企业' },
  { code: 'SELLER', label: '销售商' }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const villages = ref([])
const query = reactive({ page: 1, size: 10, keyword: '', roleCode: '' })

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  username: '',
  realName: '',
  phone: '',
  roleCode: 'FARMER',
  orgName: '',
  villageId: null,
  password: '',
  status: 1
})

const rules = {
  username: [{ required: true, message: '请填写登录账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const load = async () => {
  loading.value = true
  try {
    const res = await userPage({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      roleCode: query.roleCode || undefined
    })
    rows.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const reload = () => {
  query.page = 1
  load()
}

const reset = () => {
  query.keyword = ''
  query.roleCode = ''
  reload()
}

const openCreate = () => {
  Object.assign(form, {
    id: null,
    username: '',
    realName: '',
    phone: '',
    roleCode: 'FARMER',
    orgName: '',
    villageId: store.roleCode === 'VILLAGE_ADMIN' ? store.user?.villageId : null,
    password: '',
    status: 1
  })
  dialogVisible.value = true
}

const openEdit = (row) => {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    roleCode: row.roleCode,
    orgName: row.orgName,
    villageId: row.villageId,
    password: '',
    status: 1
  })
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateUser(form.id, form)
        ElMessage.success('用户已更新')
      } else {
        await createUser(form)
        ElMessage.success('用户已创建，初始密码为 ' + (form.password || '123456'))
      }
      dialogVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

const doReset = async (row) => {
  await ElMessageBox.confirm(`确认将 ${row.realName} 的密码重置为 123456？`, '重置密码', { type: 'warning' })
  await resetPassword(row.id, '123456')
  ElMessage.success('密码已重置为 123456')
}

onMounted(async () => {
  load()
  try {
    const res = await villageList()
    villages.value = res.data || []
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
