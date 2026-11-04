<template>
  <div class="page-plain">
    <van-nav-bar title="农事记录" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="filter">
      <van-search
        v-model="keyword"
        placeholder="按批次号搜索"
        shape="round"
        background="transparent"
        @search="reload"
        @clear="reload"
      />
    </div>

    <div class="summary">
      <div class="sum-item">
        <div class="sum-num">{{ total }}</div>
        <div class="sum-label">记录总数</div>
      </div>
      <div class="sum-item">
        <div class="sum-num">{{ chainCount }}</div>
        <div class="sum-label">已上链</div>
      </div>
      <div class="sum-item">
        <div class="sum-num">{{ pendingCount }}</div>
        <div class="sum-label">待审核</div>
      </div>
    </div>

    <div class="list">
      <div v-for="r in rows" :key="r.id" class="item">
        <div class="item-top">
          <div class="type">
            <van-icon :name="iconOf(r.recordType)" />
            <span>{{ typeLabel(r.recordType) }}</span>
          </div>
          <span class="chain" :class="r.chainStatus">{{ chainLabel(r.chainStatus) }}</span>
        </div>

        <div class="content">{{ r.content }}</div>

        <div class="tags" v-if="r.materialName || r.dosage">
          <span v-if="r.materialName" class="tag">投入品：{{ r.materialName }}</span>
          <span v-if="r.dosage" class="tag">用量：{{ r.dosage }}</span>
        </div>

        <div class="imgs" v-if="imagesOf(r).length">
          <van-image
            v-for="(u, i) in imagesOf(r)"
            :key="i"
            :src="u"
            width="64"
            height="64"
            radius="8"
            fit="cover"
            @click="preview(r, i)"
          />
        </div>

        <div class="item-foot">
          <div class="foot-left">
            <span>{{ r.operatorName || '—' }}</span>
            <span class="dot">·</span>
            <span>{{ r.operationDate }}</span>
          </div>
          <span class="audit" :class="r.auditStatus">{{ auditLabel(r.auditStatus) }}</span>
        </div>

        <div class="batch-line" @click="$router.push(`/batch/${r.batchId}`)">
          <van-icon name="label-o" />
          <span class="mono">{{ r.batchNo }}</span>
          <van-icon name="arrow" class="arrow" />
        </div>
      </div>
    </div>

    <van-empty v-if="!loading && !rows.length" description="暂无农事记录" />

    <div class="more" v-if="hasMore">
      <van-button size="small" plain round :loading="loading" @click="loadMore">加载更多</van-button>
    </div>

    <div class="placeholder-bottom"></div>

    <div class="fab">
      <van-button type="primary" round block icon="plus" @click="goCreate">新增农事记录</van-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showImagePreview } from 'vant'
import { plantingPage } from '@/api'

const router = useRouter()
const keyword = ref('')
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)

const chainCount = ref(0)
const pendingCount = ref(0)

const TYPE_MAP = {
  SOIL: '整地施肥',
  SOWING: '播种定植',
  FERTILIZE: '追肥',
  IRRIGATE: '灌溉',
  PEST: '病虫害防治',
  WEED: '除草',
  OTHER: '其他农事'
}

const typeLabel = (t) => TYPE_MAP[t] || t

const iconOf = (t) =>
  ({
    SOIL: 'flower-o',
    SOWING: 'seedling-o',
    FERTILIZE: 'nutrition-o',
    IRRIGATE: 'water-o',
    PEST: 'warning-o',
    WEED: 'clear',
    OTHER: 'edit'
  }[t] || 'edit')

const chainLabel = (s) => ({ SUCCESS: '已上链', FAILED: '上链失败', SKIPPED: '演示模式' }[s] || '未上链')

const auditLabel = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || '待审核')

const imagesOf = (r) => (r.images ? r.images.split(',').map((s) => s.trim()).filter(Boolean) : [])

const preview = (r, index) => {
  showImagePreview({ images: imagesOf(r), startPosition: index })
}

const fetch = async (reset = false) => {
  loading.value = true
  try {
    if (reset) page.value = 1
    const res = await plantingPage({
      page: page.value,
      size: 10,
      batchNo: keyword.value || undefined
    })
    const list = res.data.records || []
    rows.value = reset ? list : [...rows.value, ...list]
    total.value = res.data.total || 0
    hasMore.value = rows.value.length < total.value
    if (reset) {
      chainCount.value = list.filter((x) => x.chainStatus === 'SUCCESS').length
      pendingCount.value = list.filter((x) => x.auditStatus === 'PENDING').length
    }
  } finally {
    loading.value = false
  }
}

const reload = () => fetch(true)
const loadMore = () => {
  page.value += 1
  fetch(false)
}

const goCreate = () => router.push('/planting/new/0')

onMounted(reload)
</script>

<style scoped>
.filter {
  background: #fff;
  padding: 4px 0;
}

.summary {
  display: flex;
  background: #fff;
  margin: 12px 14px 0;
  border-radius: 14px;
  padding: 14px 0;
  box-shadow: 0 4px 14px rgba(31, 61, 45, 0.04);
}

.sum-item {
  flex: 1;
  text-align: center;
  border-right: 1px solid #f0f4f2;
}

.sum-item:last-child {
  border-right: none;
}

.sum-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--primary-dark);
}

.sum-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 3px;
}

.list {
  padding: 10px 14px 0;
}

.item {
  background: #fff;
  border-radius: 14px;
  padding: 14px 15px;
  margin-bottom: 10px;
  box-shadow: 0 4px 14px rgba(31, 61, 45, 0.04);
}

.item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.type {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
}

.type .van-icon {
  color: var(--primary);
  font-size: 16px;
}

.chain {
  font-size: 10px;
  color: #8b9aaa;
  background: #f3f6f8;
  padding: 2px 7px;
  border-radius: 999px;
}

.chain.SUCCESS {
  color: #2f9e63;
  background: #eaf7f0;
}

.chain.FAILED {
  color: #e05252;
  background: #fdeeee;
}

.content {
  font-size: 13px;
  color: #3d4d5c;
  line-height: 1.7;
  margin-top: 9px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.tag {
  font-size: 11px;
  color: #5a6b7b;
  background: #f4f7f5;
  padding: 3px 8px;
  border-radius: 6px;
}

.imgs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.item-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 11px;
}

.foot-left {
  font-size: 11px;
  color: var(--text-secondary);
}

.foot-left .dot {
  margin: 0 5px;
  color: #c3ccd6;
}

.audit {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #fff6e8;
  color: #d99a2b;
}

.audit.APPROVED {
  background: #eaf7f0;
  color: #2f9e63;
}

.audit.REJECTED {
  background: #fdeeee;
  color: #e05252;
}

.batch-line {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 11px;
  padding-top: 10px;
  border-top: 1px dashed #eef2f5;
  font-size: 11px;
  color: #6b7c8f;
}

.batch-line .arrow {
  margin-left: auto;
  color: #c3ccd6;
}

.more {
  text-align: center;
  padding: 6px 0 16px;
}

.placeholder-bottom {
  height: 72px;
}

.fab {
  position: fixed;
  left: 14px;
  right: 14px;
  bottom: calc(12px + env(safe-area-inset-bottom));
  z-index: 20;
}

.fab :deep(.van-button) {
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 20px rgba(47, 158, 99, 0.32);
}
</style>
