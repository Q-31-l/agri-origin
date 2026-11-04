<template>
  <div class="page">
    <div class="hero">
      <div class="avatar">{{ (store.displayName || '农').slice(0, 1) }}</div>
      <div class="name">{{ store.displayName || '未登录' }}</div>
      <div class="role">
        <span class="role-tag">{{ store.user?.roleName || '—' }}</span>
        <span class="org">{{ store.user?.villageName || store.user?.orgName || '' }}</span>
      </div>
      <div class="stats">
        <div class="s-item">
          <div class="s-num">{{ stats.batch }}</div>
          <div class="s-label">我的批次</div>
        </div>
        <div class="s-item">
          <div class="s-num">{{ stats.planting }}</div>
          <div class="s-label">农事记录</div>
        </div>
        <div class="s-item">
          <div class="s-num">{{ stats.harvest }}</div>
          <div class="s-label">采收记录</div>
        </div>
      </div>
    </div>

    <van-cell-group inset title="业务入口">
      <van-cell title="我的批次" icon="orders-o" is-link to="/batches" />
      <van-cell title="农事记录" icon="edit" is-link to="/planting" />
      <van-cell title="采收提交" icon="flower-o" is-link to="/harvest" />
    </van-cell-group>

    <van-cell-group inset title="账号信息">
      <van-cell title="账号" :value="store.user?.username || '—'" />
      <van-cell title="真实姓名" :value="store.user?.realName || '—'" />
      <van-cell title="联系电话" :value="store.user?.phone || '—'" />
      <van-cell title="所属组织" :value="store.user?.orgName || store.user?.villageName || '—'" />
      <van-cell title="数据范围" :value="scopeLabel" />
    </van-cell-group>

    <van-cell-group inset title="关于平台">
      <van-cell title="区块链网络" :value="chain.enabled ? 'FISCO-BCOS 2.9' : '演示模式'" />
      <van-cell title="联盟链状态" :value="chain.enabled ? (chain.ready ? '已连接' : '连接异常') : '未接入'" />
      <van-cell title="数据存证" value="SHA-256 摘要上链" />
      <van-cell title="版本" value="v1.0.0" />
    </van-cell-group>

    <div class="logout">
      <van-button block round plain type="danger" @click="onLogout">退出登录</van-button>
    </div>

    <div class="foot">农产品质量安全溯源平台 · 毕设演示版</div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { batchPage, chainStatus, harvestPage, plantingPage } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const router = useRouter()

const chain = ref({ enabled: false, ready: false })
const stats = reactive({ batch: 0, planting: 0, harvest: 0 })

const scopeLabel = computed(
  () =>
    ({
      ALL: '全平台数据',
      VILLAGE: '本行政村数据',
      ORG: '本组织数据',
      SELF: '仅本人数据'
    }[store.user?.dataScope] || store.user?.dataScope || '—')
)

const onLogout = async () => {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
    store.logout()
    router.replace('/login')
  } catch (e) {
    /* 取消 */
  }
}

onMounted(async () => {
  try {
    store.refresh()
  } catch (e) {
    /* ignore */
  }
  try {
    const [b, p, h, c] = await Promise.all([
      batchPage({ page: 1, size: 1 }),
      plantingPage({ page: 1, size: 1 }),
      harvestPage({ page: 1, size: 1 }),
      chainStatus()
    ])
    stats.batch = b.data.total || 0
    stats.planting = p.data.total || 0
    stats.harvest = h.data.total || 0
    chain.value = c.data || chain.value
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.hero {
  background: linear-gradient(160deg, #123626 0%, #1f5c3e 65%, #2f8556 100%);
  color: #eaf6ef;
  padding: 34px 20px 26px;
  border-radius: 0 0 24px 24px;
  text-align: center;
}

.avatar {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 25px;
  font-weight: 600;
}

.name {
  font-size: 19px;
  font-weight: 700;
  margin-top: 12px;
}

.role {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
}

.role-tag {
  font-size: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
}

.org {
  font-size: 11px;
  color: rgba(207, 227, 216, 0.78);
}

.stats {
  display: flex;
  margin-top: 22px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  padding: 13px 0;
}

.s-item {
  flex: 1;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.s-item:last-child {
  border-right: none;
}

.s-num {
  font-size: 20px;
  font-weight: 700;
}

.s-label {
  font-size: 10px;
  color: rgba(207, 227, 216, 0.72);
  margin-top: 4px;
}

.logout {
  padding: 22px 18px 0;
}

.foot {
  text-align: center;
  font-size: 10px;
  color: #a8b4c0;
  padding: 20px 0 30px;
}

:deep(.van-cell-group__title) {
  padding: 18px 30px 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
