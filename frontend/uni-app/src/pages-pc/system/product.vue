<template>
  <pc-shell title="产品品类">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">产品品类</text>
          <text class="pc-sub">品类、品种与保质期维护，新建批次时从这里选择产品</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增品类</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.keyword"
            placeholder="搜索产品名称 / 品类"
            @confirm="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-code">产品编码</text>
          <text class="pc-th w-name">产品名称</text>
          <text class="pc-th">品类</text>
          <text class="pc-th">品种</text>
          <text class="pc-th">单位</text>
          <text class="pc-th">保质期(天)</text>
          <text class="pc-th w-region">产地</text>
          <text class="pc-th w-desc">描述</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无产品品类</view>
        <view v-else class="pc-tbody">
          <view v-for="p in list" :key="p.id" class="pc-tr">
            <text class="pc-td w-code mono">{{ p.productCode || '-' }}</text>
            <text class="pc-td w-name strong">{{ p.productName }}</text>
            <text class="pc-td">{{ p.category || '-' }}</text>
            <text class="pc-td">{{ p.variety || '-' }}</text>
            <text class="pc-td">{{ p.unit || '-' }}</text>
            <text class="pc-td">{{ p.shelfLifeDays || '-' }}</text>
            <text class="pc-td w-region">{{ p.originRegion || '-' }}</text>
            <view class="pc-td w-desc">{{ p.description || '-' }}</view>
            <view class="pc-td w-act">
              <text v-if="canUpdate" class="pc-link" @click="openEdit(p)">编辑</text>
              <text v-else class="pc-link mute">—</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal
        v-model="formVisible"
        :title="editing ? '编辑品类' : '新增品类'"
        width="540px"
        :ok-text="editing ? '保存' : '创建'"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">产品名称<text class="req">*</text></text>
              <input class="pc-input" v-model="form.productName" placeholder="如：白菜" />
            </view>
            <view class="pc-field">
              <text class="pc-label">品类</text>
              <input class="pc-input" v-model="form.category" placeholder="如：叶菜类" />
            </view>
            <view class="pc-field">
              <text class="pc-label">品种</text>
              <input class="pc-input" v-model="form.variety" placeholder="如：上海青" />
            </view>
            <view class="pc-field">
              <text class="pc-label">计量单位</text>
              <pc-select v-model="form.unit" :options="unitOptions" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">保质期（天）</text>
              <input class="pc-input" v-model="form.shelfLifeDays" type="number" placeholder="如：7" />
            </view>
            <view class="pc-field">
              <text class="pc-label">产地</text>
              <input class="pc-input" v-model="form.originRegion" placeholder="如：广东省清远市" />
            </view>
            <view class="pc-field full">
              <text class="pc-label">产品描述</text>
              <textarea
                class="pc-textarea"
                v-model="form.description"
                placeholder="品种特性、种植方式等"
                maxlength="200"></textarea>
            </view>
          </view>
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
import { productPage, createProduct, updateProduct } from '@/api'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/ui'

const store = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, keyword: '' })

const canCreate = computed(() => store.hasPerm('product:create'))
const canUpdate = computed(() => store.hasPerm('product:update'))

const unitOptions = ['kg', '吨', '斤', '箱', '件'].map((u) => ({ value: u, label: u }))

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword.trim()
    const res = await productPage(params)
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
  query.page = 1
  load()
}

function onPage(p) {
  query.page = p
  load()
}

const formVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)

const form = reactive({
  id: null,
  productName: '',
  category: '',
  variety: '',
  unit: 'kg',
  shelfLifeDays: '',
  originRegion: '',
  description: ''
})

function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null,
    productName: '',
    category: '',
    variety: '',
    unit: 'kg',
    shelfLifeDays: '',
    originRegion: '',
    description: ''
  })
  formVisible.value = true
}

function openEdit(p) {
  editing.value = true
  Object.assign(form, {
    id: p.id,
    productName: p.productName || '',
    category: p.category || '',
    variety: p.variety || '',
    unit: p.unit || 'kg',
    shelfLifeDays: p.shelfLifeDays || '',
    originRegion: p.originRegion || '',
    description: p.description || ''
  })
  formVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.productName.trim()) return toast('请填写产品名称')

  const payload = {
    productName: form.productName.trim(),
    category: form.category,
    variety: form.variety,
    unit: form.unit,
    originRegion: form.originRegion,
    description: form.description
  }
  if (form.shelfLifeDays) payload.shelfLifeDays = Number(form.shelfLifeDays)

  submitting.value = true
  try {
    if (editing.value) await updateProduct(form.id, payload)
    else await createProduct(payload)
    uni.showToast({ title: editing.value ? '已保存' : '创建成功', icon: 'success' })
    formVisible.value = false
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-code { flex: 1.3; }
.w-name { flex: 1.3; }
.w-region { flex: 1.3; }
.w-desc { flex: 1.8; }
.w-act { flex: 0.7; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
</style>
