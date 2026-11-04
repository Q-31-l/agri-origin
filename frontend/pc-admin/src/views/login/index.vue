<template>
  <div class="login">
    <div class="visual">
      <div class="visual-inner">
        <div class="logo">
          <div class="logo-mark">溯</div>
          <div>
            <div class="logo-title">农安链溯源</div>
            <div class="logo-sub">AGRI-ORIGIN ON FISCO-BCOS</div>
          </div>
        </div>

        <h1 class="headline">
          从田间到餐桌<br />
          每一步都<em>上链可信</em>
        </h1>
        <p class="subline">
          以农产品批次为主线，覆盖种植采收、质量检测、物流运输、终端销售全流程，
          关键数据与文件摘要实时存证于联盟链，不可篡改、可追溯、可定责。
        </p>

        <div class="features">
          <div class="feature">
            <el-icon><Lock /></el-icon>
            <div>
              <div class="f-title">联盟链存证</div>
              <div class="f-desc">SHA-256 摘要 + 交易回执双重校验</div>
            </div>
          </div>
          <div class="feature">
            <el-icon><Aim /></el-icon>
            <div>
              <div class="f-title">异常熔断</div>
              <div class="f-desc">不合格批次全链路禁售并定责</div>
            </div>
          </div>
          <div class="feature">
            <el-icon><Grid /></el-icon>
            <div>
              <div class="f-title">网格监管</div>
              <div class="f-desc">村级管理员一对多辖区管控</div>
            </div>
          </div>
        </div>

        <div class="visual-foot">FISCO-BCOS 2.9 · Spring Boot · Vue 3 · MySQL 8</div>
      </div>
      <div class="orb orb-a"></div>
      <div class="orb orb-b"></div>
    </div>

    <div class="panel">
      <div class="panel-inner">
        <div class="panel-head">
          <h2>欢迎回来</h2>
          <p>请使用系统分配的账号登录管理端</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="登录账号" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="登录密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
        </el-form>

        <div class="quick">
          <div class="quick-title">演示账号（密码均为 123456）</div>
          <div class="quick-list">
            <button
              v-for="item in accounts"
              :key="item.username"
              class="quick-item"
              :class="{ active: form.username === item.username }"
              @click="fill(item)"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="panel-foot">
          消费者溯源查询无需登录，可使用移动端扫码访问
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const accounts = [
  { label: '管理员', username: 'admin' },
  { label: '生产主体', username: 'producer01' },
  { label: '村级管理员', username: 'village01' },
  { label: '检测机构', username: 'inspector01' },
  { label: '物流企业', username: 'logistics01' },
  { label: '销售商', username: 'seller01' },
  { label: '监管人员', username: 'regulator01' },
  { label: '农户', username: 'farmer01' }
]

const fill = (item) => {
  form.username = item.username
  form.password = '123456'
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await store.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/dashboard'
      router.replace(redirect)
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login {
  display: flex;
  min-height: 100vh;
  background: #f3f7f4;
}

.visual {
  flex: 1.15;
  background: linear-gradient(150deg, #102f22 0%, #1b5138 52%, #2a6b49 100%);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 0 7vw;
}

.visual::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.09) 1px, transparent 1px);
  background-size: 26px 26px;
  opacity: 0.5;
}

.visual-inner {
  position: relative;
  z-index: 2;
  max-width: 560px;
  color: #eaf6ef;
}

.logo {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo-mark {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, #8fe6b4, #2f9e63);
  color: #0d2c1e;
  font-size: 26px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.3);
}

.logo-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.logo-sub {
  font-size: 10px;
  letter-spacing: 2px;
  color: rgba(207, 227, 216, 0.6);
  margin-top: 4px;
}

.headline {
  font-size: 44px;
  line-height: 1.28;
  font-weight: 700;
  margin: 56px 0 20px;
  letter-spacing: 1px;
}

.headline em {
  font-style: normal;
  color: #8fe6b4;
  position: relative;
}

.headline em::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 4px;
  height: 8px;
  background: rgba(143, 230, 180, 0.22);
  border-radius: 4px;
}

.subline {
  font-size: 14px;
  line-height: 1.9;
  color: rgba(214, 234, 223, 0.78);
  max-width: 480px;
}

.features {
  display: flex;
  gap: 14px;
  margin-top: 44px;
  flex-wrap: wrap;
}

.feature {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 12px;
  padding: 14px;
  width: 168px;
  backdrop-filter: blur(4px);
}

.feature .el-icon {
  color: #8fe6b4;
  font-size: 18px;
  margin-top: 2px;
}

.f-title {
  font-size: 13px;
  font-weight: 600;
}

.f-desc {
  font-size: 11px;
  color: rgba(214, 234, 223, 0.6);
  margin-top: 4px;
  line-height: 1.5;
}

.visual-foot {
  margin-top: 52px;
  font-size: 11px;
  color: rgba(207, 227, 216, 0.4);
  letter-spacing: 1px;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}

.orb-a {
  width: 380px;
  height: 380px;
  right: -120px;
  top: -100px;
  background: radial-gradient(circle, rgba(143, 230, 180, 0.28), transparent 65%);
}

.orb-b {
  width: 460px;
  height: 460px;
  left: -160px;
  bottom: -220px;
  background: radial-gradient(circle, rgba(217, 164, 65, 0.16), transparent 65%);
}

.panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.panel-inner {
  width: 400px;
}

.panel-head h2 {
  font-size: 28px;
  margin: 0;
  letter-spacing: 1px;
}

.panel-head p {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 10px 0 32px;
}

.submit {
  width: 100%;
  height: 46px;
  font-size: 15px;
  letter-spacing: 6px;
  border-radius: 10px;
  background: linear-gradient(90deg, #2f9e63, #43b47a);
  border: none;
  margin-top: 6px;
}

.quick {
  margin-top: 34px;
  border-top: 1px dashed var(--border);
  padding-top: 22px;
}

.quick-title {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.quick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-item {
  border: 1px solid var(--border);
  background: #fff;
  color: #4b5c6d;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.quick-item:hover,
.quick-item.active {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light);
}

.panel-foot {
  margin-top: 28px;
  font-size: 11px;
  color: #9aa8b6;
  text-align: center;
}

@media (max-width: 1000px) {
  .visual {
    display: none;
  }
}
</style>
