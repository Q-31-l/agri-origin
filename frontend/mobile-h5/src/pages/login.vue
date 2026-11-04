<template>
  <div class="login">
    <div class="hero">
      <div class="brand">
        <div class="mark">溯</div>
        <div>
          <div class="name">农安链溯源</div>
          <div class="sub">AGRI-ORIGIN</div>
        </div>
      </div>
      <h1>每一份农产品<br />都有可信的来路</h1>
      <p>农户现场记录 · 村管审核 · 全流程上链存证</p>
      <div class="hero-chips">
        <span>种植采收</span>
        <span>质量检测</span>
        <span>物流销售</span>
      </div>
    </div>

    <div class="form-wrap">
      <div class="form-card">
        <div class="form-title">账号登录</div>
        <van-cell-group inset>
          <van-field v-model="form.username" label="账号" placeholder="请输入登录账号" clearable />
          <van-field v-model="form.password" type="password" label="密码" placeholder="请输入密码" />
        </van-cell-group>
        <div class="btn-wrap">
          <van-button type="primary" block round :loading="loading" @click="submit">登 录</van-button>
        </div>

        <div class="quick">
          <div class="quick-title">演示账号（密码 123456）</div>
          <div class="quick-list">
            <van-tag v-for="a in accounts" :key="a.username" :plain="form.username !== a.username" size="large" @click="fill(a)">
              {{ a.label }}
            </van-tag>
          </div>
        </div>
      </div>

      <div class="consumer" @click="$router.push('/trace')">
        <van-icon name="scan" />
        <span>消费者扫码溯源入口</span>
        <van-icon name="arrow" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const loading = ref(false)
const form = reactive({ username: 'farmer01', password: '123456' })

const accounts = [
  { label: '农户', username: 'farmer01' },
  { label: '村级管理员', username: 'village01' },
  { label: '生产主体', username: 'producer01' }
]

const fill = (a) => {
  form.username = a.username
  form.password = '123456'
}

const submit = async () => {
  if (!form.username || !form.password) return showToast('请输入账号和密码')
  loading.value = true
  try {
    await store.login(form)
    showSuccessToast('登录成功')
    router.replace(route.query.redirect || '/home')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  background: #f4f7f5;
}

.hero {
  height: 300px;
  background: linear-gradient(160deg, #123626 0%, #1f5c3e 60%, #2f8556 100%);
  padding: 28px 24px 0;
  color: #eaf6ef;
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -60px;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(143, 230, 180, 0.28), transparent 65%);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(135deg, #8fe6b4, #2f9e63);
  color: #0d2c1e;
  font-weight: 800;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.name {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
}

.sub {
  font-size: 9px;
  letter-spacing: 2px;
  color: rgba(207, 227, 216, 0.55);
  margin-top: 3px;
}

h1 {
  font-size: 26px;
  line-height: 1.45;
  margin: 40px 0 12px;
  letter-spacing: 1px;
}

p {
  font-size: 12px;
  color: rgba(207, 227, 216, 0.7);
  margin: 0;
}

.hero-chips {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}

.hero-chips span {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.form-wrap {
  margin-top: -44px;
  padding: 0 0 30px;
  position: relative;
  z-index: 2;
}

.form-card {
  background: #fff;
  border-radius: 18px;
  margin: 0 16px;
  padding: 20px 0 12px;
  box-shadow: 0 12px 30px rgba(20, 60, 44, 0.1);
}

.form-title {
  font-size: 16px;
  font-weight: 700;
  padding: 0 20px 10px;
}

.btn-wrap {
  padding: 18px 20px 6px;
}

.quick {
  padding: 10px 20px 16px;
}

.quick-title {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}

.quick-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.consumer {
  margin: 18px 20px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #6b7c8f;
  font-size: 13px;
  padding: 12px;
}
</style>
