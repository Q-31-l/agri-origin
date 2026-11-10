<template>
  <pc-shell title="异常管控">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">异常管控</text>
          <text class="pc-sub">异常自动熔断物流与销售写入，处置结果与哈希同步上链</text>
        </view>
      </view>

      <view class="pc-stats">
        <view class="pc-stat">
          <text class="pc-stat-label">异常记录总数</text>
          <text class="pc-stat-value red">{{ total }}</text>
          <text class="pc-stat-hint">含已处置与待处置</text>
        </view>
        <view class="pc-stat">
          <text class="pc-stat-label">待处置</text>
          <text class="pc-stat-value orange">{{ pendingCount }}</text>
          <text class="pc-stat-hint">需尽快完成处置闭环</text>
        </view>
        <view class="pc-stat">
          <text class="pc-stat-label">已处置</text>
          <text class="pc-stat-value green">{{ disposedCount }}</text>
          <text class="pc-stat-hint">处置结果已上链</text>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.batchNo"
            placeholder="按批次编码查询"
            @confirm="search"
          />
          <pc-select
            v-model="query.status"
            :options="statusOptions"
            placeholder="全部状态"
            width="150px"
            @change="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-batch">批次编码</text>
          <text class="pc-th">异常类型</text>
          <text class="pc-th w-desc">异常描述</text>
          <text class="pc-th w-measure">处置措施</text>
          <text class="pc-th w-res">处置结果</text>
          <text class="pc-th">处置人</text>
          <text class="pc-th w-time">处置时间</text>
          <text class="pc-th w-status">状态</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无异常记录</view>
        <view v-else class="pc-tbody">
          <view v-for="r in list" :key="r.id" class="pc-tr">
            <text class="pc-td w-batch mono">{{ r.batchNo || '-' }}</text>
            <text class="pc-td">{{ abnormalTypeLabel(r.abnormalType) }}</text>
            <view class="pc-td w-desc wrap">{{ r.description || '-' }}</view>
            <text class="pc-td w-measure wrap">{{ r.disposalMeasure || '-' }}</text>
            <view class="pc-td w-res wrap">{{ r.disposalResult || '-' }}</view>
            <text class="pc-td">{{ r.handlerName || '-' }}</text>
            <text class="pc-td w-time">{{ formatDateTime(r.handleTime) }}</text>
            <view class="pc-td w-status">
              <text class="st" :style="statusStyle(r.status)">
                {{ abnormalStatusLabel(r.status) }}
              </text>
            </view>
            <view class="pc-td w-act">
              <text v-if="canDispose && r.status !== 'DONE'" class="pc-link" @click="openDispose(r)">
                处置
              </text>
              <text v-else class="pc-link mute">—</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <!-- 处置弹窗 -->
      <pc-modal
        v-model="disposeVisible"
        title="异常处置登记"
        width="520px"
        ok-text="提交处置并上链"
        @ok="submitDispose"
      >
        <view class="pc-form">
          <view class="batch-bar">
            <text class="bb-no mono">{{ current.batchNo }}</text>
            <text class="bb-type">{{ current.abnormalType }}</text>
          </view>
          <view class="pc-field full">
            <text class="pc-label">异常类型</text>
            <pc-select v-model="disposeForm.abnormalType" :options="typeOptions" width="100%" />
          </view>
          <view class="pc-field full">
            <text class="pc-label">处置措施<text class="req">*</text></text>
            <textarea
              class="pc-textarea"
              v-model="disposeForm.disposalMeasure"
              placeholder="如：整批召回并销毁、重新检测"
              maxlength="200"></textarea>
          </view>
          <view class="pc-field full">
            <text class="pc-label">处置结果<text class="req">*</text></text>
            <textarea
              class="pc-textarea"
              v-model="disposeForm.disposalResult"
              placeholder="如：已完成无害化处理，经复检合格"
              maxlength="200"></textarea>
          </view>
          <view class="pc-field full">
            <text class="pc-label">实证材料（图片 / 视频）</text>
            <evidence-uploader
              ref="uploader"
              v-model="disposeForm.evidences"
              biz-type="DISPOSAL"
              :max="6" />
          </view>
        </view>
      </pc-modal>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useRealtime } from '@/composables/useRealtime'
import PcShell from '@/layouts/pc-shell.vue'
import PcSelect from '@/components/pc/pc-select.vue'
import PcPager from '@/components/pc/pc-pager.vue'
import PcModal from '@/components/pc/pc-modal.vue'
import EvidenceUploader from '@/components/business/evidence-uploader.vue'
import { abnormalPage, disposeAbnormal } from '@/api'
import { useUserStore } from '@/store/user'
import { useCacheStore } from '@/store/cache'
import { toast } from '@/utils/ui'
import { formatDateTime } from '@/utils/format'
import {
  ABNORMAL_STATUS,
  ABNORMAL_TYPE_OPTIONS,
  abnormalStatusLabel,
  abnormalTypeLabel
} from '@/config/constants'

const store = useUserStore()
const cache = useCacheStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, batchNo: '', status: '' })

const canDispose = computed(() => store.hasPerm('abnormal:dispose'))

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'HANDLING', label: '处置中' },
  { value: 'DONE', label: '已处置' }
]

const typeOptions = ABNORMAL_TYPE_OPTIONS

const pendingCount = computed(() => list.value.filter((i) => i.status !== 'DONE').length)
const disposedCount = computed(() => list.value.filter((i) => i.status === 'DONE').length)

function statusStyle(s) {
  const c = ABNORMAL_STATUS[s] || { color: '#64748b', bg: '#f1f2f4' }
  return { color: c.color, background: c.bg }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.batchNo) params.batchNo = query.batchNo.trim()
    if (query.status) params.status = query.status
    const res = await abnormalPage(params)
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
  query.batchNo = ''
  query.status = ''
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

/* ---------------- 处置 ---------------- */
const disposeVisible = ref(false)
const current = ref({})
const submitting = ref(false)

const disposeForm = reactive({
  abnormalType: 'OTHER',
  disposalMeasure: '',
  disposalResult: '',
  evidences: []
})
const uploader = ref(null)

function openDispose(r) {
  current.value = r
  disposeForm.abnormalType = r.abnormalType || 'OTHER'
  disposeForm.disposalMeasure = ''
  disposeForm.disposalResult = ''
  disposeForm.evidences = []
  disposeVisible.value = true
}

async function submitDispose() {
  if (submitting.value) return
  if (!disposeForm.disposalMeasure.trim()) return toast('请填写处置措施')
  if (!disposeForm.disposalResult.trim()) return toast('请填写处置结果')
  if (uploader.value && uploader.value.isUploading()) return toast('实证材料仍在上传，请稍候')

  submitting.value = true
  try {
    await disposeAbnormal(current.value.id, {
      abnormalType: disposeForm.abnormalType,
      disposalMeasure: disposeForm.disposalMeasure,
      disposalResult: disposeForm.disposalResult,
      evidenceIds: disposeForm.evidences.map((f) => f.id)
    })
    uni.showToast({ title: '处置已登记', icon: 'success' })
    disposeVisible.value = false
    cache.invalidate('batch', 'abnormal')
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

onShow(load)

// 手机端登记处置后，PC 列表自动刷新（弹窗打开时跳过）
useRealtime(() => {
  if (disposeVisible.value || submitting.value) return
  load()
}, { types: ['abnormal', 'batch'] })
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-batch { flex: 1.2; }
.w-desc { flex: 2.5; }
.w-measure { flex: 1.6; }
.w-res { flex: 1.2; }
.w-time { flex: 1.5; }
.w-status { flex: 0.8; }
.w-act { flex: 0.6; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
.st {
  font-size: 10.5px;
  border-radius: 3px;
  padding: 1px 6px;
}
.st.done {
  color: #16a34a;
  background: #e8f7ee;
}
.st.wait {
  color: #d93026;
  background: #fdeceb;
}
.batch-bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  background: #fdeceb;
  border-radius: 6px;
  padding: 8px 10px;
}
.bb-no {
  font-size: 12px;
  color: #1f2937;
  font-weight: 500;
}
.bb-type {
  font-size: 11px;
  color: #d93026;
}
</style>
