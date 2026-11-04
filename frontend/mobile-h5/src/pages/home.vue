<template>
  <div class="page home">
    <div class="hero">
      <div class="hero-top">
        <div class="user">
          <div class="avatar">{{ (store.displayName || '农').slice(0, 1) }}</div>
          <div>
            <div class="hello">你好，{{ store.displayName }}</div>
            <div class="role">{{ store.user?.roleName }} · {{ store.user?.villageName || store.user?.orgName || '' }}</div>
          </div>
        </div>
        <div class="chain" :class="{ ok: chain.enabled && chain.ready }">
          <span class="dot"></span>{{ chain.enabled ? (chain.ready ? '已上链' : '链异常') : '演示模式' }}
        </div>
      </div>

      <div class="hero-nums">
        <div class="num-item">
          <div class="num">{{ totalBatches }}</div>
          <div class="num-label">我的批次</div>
        </div>
        <div class="num-item">
          <div class="num">{{ plantingTotal }}</div>
          <div class="num-label">农事记录</div>
        </div>
        <div class="num-item">
          <div class="num">{{ harvestTotal }}</div>
          <div class="num-label">采收提交</div>
        </div>
      </div>
    </div>

    <div class="quick-grid">
      <div v-for="item in actions" :key="item.path" class="quick" @click="$router.push(item.path)">
        <div class="quick-icon" :style="{ background: item.bg, color: item.color }">
          <van-icon :name="item.icon" />
        </div>
        <div class="quick-title">{{ item.title }}</div>
        <div class="quick-desc">{{ item.desc }}</div>
      </div>
    </div>

    <div class="section-title">近期批次</div>
    <div v-if="batches.length" class="batch-list">
      <div v-for="b in batches" :key="b.id" class="batch" @click="$router.push(`/batch/${b.id}`)">
        <div class="batch-top">
          <div class="batch-no">{{ b.batchNo }}</div>
          <span class="status" :class="b.status">{{ statusLabel(b.status) }}</span>
        </div>
        <div class="batch-info">
          <span>{{ b.productName }}</span>
          <span class="dot">·</span>
          <span>{{ b.quantity }}{{ b.unit }}</span>
          <span class="dot">·</span>
          <span>{{ b.plantingDate || '—' }}</span>
        </div>
        <div class="batch-foot">
          <span class="muted">{{ b.farmName || b.villageName }}</span>
          <van-icon name="arrow" color="#c3ccd6" />
        </div>
      </div>
    </div>
    <van-empty v-else description="暂无批次数据" />

    <div class="tip-card">
      <van-icon name="info-o" />
      <div>
        <div class="tip-title">数据可信说明</div>
        <div class="tip-text">
          你提交的每一条农事与采收记录都会计算 SHA-256 摘要并写入联盟链，任何修改都会留下痕迹。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { batchPage, chainStatus, harvestPage, plantingPage } from '@/api'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const chain = ref({ enabled: false, ready: false })
const batches = ref([])
const totalBatches = ref(0)
const plantingTotal = ref(0)
const harvestTotal = ref(0)

const actions = [
  { title: '农事记录', desc: '日常作业登记', icon: 'edit', path: '/planting', bg: '#eaf7f0', color: '#2f9e63' },
  { title: '采收提交', desc: '产量与照片', icon: 'flower-o', path: '/harvest', bg: '#fff5e8', color: '#d99a2b' },
  { title: '扫码溯源', desc: '查询流转履历', icon: 'scan', path: '/trace', bg: '#eaf1ff', color: '#3b82f6' },
  { title: '我的批次', desc: '查看全部批次', icon: 'orders-o', path: '/batches', bg: '#f3edff', color: '#8b5cf6' }
]

const statusLabel = (s) =>
  ({
    PLANTING: '种植中',
    HARVESTED: '已采收',
    QUALIFIED: '质检合格',
    IN_TRANSIT: '运输中',
    ON_SALE: '在售',
    SOLD: '已售出',
    ABNORMAL: '异常',
    CLOSED: '已结案'
  }[s] || s)

onMounted(async () => {
  try {
    const [b, p, h, c] = await Promise.all([
      batchPage({ page: 1, size: 5 }),
      plantingPage({ page: 1, size: 1 }),
      harvestPage({ page: 1, size: 1 }),
      chainStatus()
    ])
    batches.value = b.data.records || []
    totalBatches.value = b.data.total || 0
    plantingTotal.value = p.data.total || 0
    harvestTotal.value = h.data.total || 0
    chain.value = c.data || chain.value
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.home {
  padding-bottom: calc(76px + env(safe-area-inset-bottom));
}

.hero {
  background: linear-gradient(160deg, #123626 0%, #1f5c3e 65%, #2f8556 100%);
  padding: 22px 20px 26px;
  color: #eaf6ef;
  border-radius: 0 0 24px 24px;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
}

.hello {
  font-size: 17px;
  font-weight: 600;
}

.role {
  font-size: 11px;
  color: rgba(207, 227, 216, 0.7);
  margin-top: 4px;
}

.chain {
  font-size: 10px;
  padding: 4px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  gap: 5px;
}

.chain .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #b6c2cf;
}

.chain.ok .dot {
  background: #8fe6b4;
}

.hero-nums {
  display: flex;
  margin-top: 24px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  padding: 14px 0;
}

.num-item {
  flex: 1;
  text-align: center;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.num-item:last-child {
  border-right: none;
}

.num {
  font-size: 22px;
  font-weight: 700;
}

.num-label {
  font-size: 11px;
  color: rgba(207, 227, 216, 0.7);
  margin-top: 4px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  background: #fff;
  margin: -14px 14px 0;
  border-radius: 16px;
  padding: 16px 8px;
  box-shadow: 0 8px 22px rgba(20, 60, 44, 0.07);
  position: relative;
}

.quick {
  text-align: center;
}

.quick-icon {
  width: 42px;
  height: 42px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 8px;
  font-size: 20px;
}

.quick-title {
  font-size: 12px;
  font-weight: 600;
}

.quick-desc {
  font-size: 10px;
  color: var(--text-secondary);
  margin-top: 3px;
}

.batch-list {
  margin: 0 14px;
}

.batch {
  background: #fff;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 4px 14px rgba(31, 61, 45, 0.04);
}

.batch-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-no {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
}

.status {
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eef4f0;
  color: #2f9e63;
}

.status.ABNORMAL {
  background: #fdeeee;
  color: #e05252;
}

.status.SOLD,
.status.CLOSED {
  background: #f1f4f7;
  color: #7b8a99;
}

.status.HARVESTED {
  background: #fff6e8;
  color: #d99a2b;
}

.batch-info {
  font-size: 12px;
  color: #4a5a6a;
  margin-top: 8px;
}

.batch-info .dot {
  margin: 0 6px;
  color: #c3ccd6;
}

.batch-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f5;
}

.tip-card {
  margin: 16px 14px 0;
  background: #eef7f1;
  border-radius: 12px;
  padding: 14px;
  display: flex;
  gap: 10px;
  color: #1f7a4c;
}

.tip-title {
  font-size: 12px;
  font-weight: 600;
}

.tip-text {
  font-size: 11px;
  color: #4e7a62;
  line-height: 1.7;
  margin-top: 4px;
}
</style>
