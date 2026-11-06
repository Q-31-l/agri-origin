<template>
  <pc-shell title="农场档案">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">农场档案</text>
          <text class="pc-sub">农场基础信息与产地资质，是批次建档的归属依据</text>
        </view>
        <view class="pc-acts">
          <view v-if="canCreate" class="pc-btn" @click="openCreate">＋ 新增农场</view>
        </view>
      </view>

      <view class="pc-card">
        <view class="pc-toolbar">
          <input
            class="pc-input"
            v-model="query.keyword"
            placeholder="搜索农场名称 / 编码"
            @confirm="search"
          />
          <pc-select
            v-model="query.villageId"
            :options="villageOptions"
            placeholder="全部行政村"
            width="170px"
            @change="search"
          />
          <view class="pc-spacer"></view>
          <view class="pc-btn ghost" @click="reset">重置</view>
          <view class="pc-btn" @click="search">查询</view>
        </view>

        <view class="pc-thead">
          <text class="pc-th w-code">农场编码</text>
          <text class="pc-th w-name">农场名称</text>
          <text class="pc-th">农场主</text>
          <text class="pc-th">所属村</text>
          <text class="pc-th w-addr">地址</text>
          <text class="pc-th">面积(亩)</text>
          <text class="pc-th">土壤</text>
          <text class="pc-th">水源</text>
          <text class="pc-th w-act">操作</text>
        </view>

        <view v-if="loading" class="pc-none">加载中…</view>
        <view v-else-if="!list.length" class="pc-none">暂无农场数据</view>
        <view v-else class="pc-tbody">
          <view v-for="f in list" :key="f.id" class="pc-tr">
            <text class="pc-td w-code mono">{{ f.farmCode || '-' }}</text>
            <text class="pc-td w-name strong">{{ f.farmName }}</text>
            <text class="pc-td">{{ f.farmerName || '-' }}</text>
            <text class="pc-td">{{ f.villageName || '-' }}</text>
            <view class="pc-td w-addr">{{ f.address || '-' }}</view>
            <text class="pc-td">{{ f.area || '-' }}</text>
            <text class="pc-td">{{ f.soilType || '-' }}</text>
            <text class="pc-td">{{ f.waterSource || '-' }}</text>
            <view class="pc-td w-act">
              <text v-if="canUpdate" class="pc-link" @click="openEdit(f)">编辑</text>
              <text v-else class="pc-link mute">—</text>
            </view>
          </view>
        </view>

        <pc-pager :page="query.page" :size="query.size" :total="total" @change="onPage" />
      </view>

      <pc-modal
        v-model="formVisible"
        :title="editing ? '编辑农场' : '新增农场'"
        width="560px"
        :ok-text="editing ? '保存' : '创建'"
        @ok="submit"
      >
        <view class="pc-form">
          <view class="pc-form-grid">
            <view class="pc-field">
              <text class="pc-label">农场名称<text class="req">*</text></text>
              <input class="pc-input" v-model="form.farmName" placeholder="如：绿源蔬菜种植基地" />
            </view>
            <view class="pc-field">
              <text class="pc-label">所属行政村<text class="req">*</text></text>
              <pc-select v-model="form.villageId" :options="villageOptions" placeholder="请选择" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">农场主</text>
              <pc-select v-model="form.farmerId" :options="farmerOptions" placeholder="请选择农户" width="100%" />
            </view>
            <view class="pc-field">
              <text class="pc-label">面积（亩）</text>
              <input class="pc-input" v-model="form.area" type="digit" placeholder="0.00" />
            </view>
            <view class="pc-field">
              <text class="pc-label">土壤类型</text>
              <input class="pc-input" v-model="form.soilType" placeholder="如：壤土" />
            </view>
            <view class="pc-field">
              <text class="pc-label">灌溉水源</text>
              <input class="pc-input" v-model="form.waterSource" placeholder="如：山泉水 / 地下水" />
            </view>
            <view class="pc-field">
              <text class="pc-label">认证证书号</text>
              <input class="pc-input" v-model="form.certNo" placeholder="如：有机认证编号" />
            </view>
            <view class="pc-field">
              <text class="pc-label">状态</text>
              <pc-select v-model="form.status" :options="statusOptions" width="100%" />
            </view>
            <view class="pc-field full">
              <text class="pc-label">详细地址</text>
              <input class="pc-input" v-model="form.address" placeholder="省 / 市 / 县 / 乡镇 / 村 详细地址" />
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
import {
  farmPage,
  createFarm,
  updateFarm,
  villageList,
  userOptions
} from '@/api'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/ui'

const store = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const villages = ref([])
const farmers = ref([])

const query = reactive({ page: 1, size: 10, keyword: '', villageId: '' })

const canCreate = computed(() => store.hasPerm('farm:create'))
const canUpdate = computed(() => store.hasPerm('farm:update'))

const villageOptions = computed(() =>
  villages.value.map((v) => ({ value: v.id, label: v.villageName }))
)
const farmerOptions = computed(() =>
  farmers.value.map((u) => ({ value: u.id, label: u.realName || `用户${u.id}` }))
)
const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' }
]

async function loadBase() {
  if (!villages.value.length) {
    try {
      villages.value = (await villageList()).data || []
    } catch (e) {
      villages.value = []
    }
  }
  if (!farmers.value.length) {
    try {
      // 用最小字段集接口：/user/list 需要 user:list，生产主体没有该权限，
      // 原来这里直接 403，导致"新建农场"选不了负责人
      farmers.value = (await userOptions({ roleCode: 'FARMER' })).data || []
    } catch (e) {
      farmers.value = []
    }
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword) params.keyword = query.keyword.trim()
    if (query.villageId) params.villageId = query.villageId
    const res = await farmPage(params)
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
  query.villageId = ''
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
  farmName: '',
  villageId: '',
  farmerId: '',
  address: '',
  area: '',
  soilType: '',
  waterSource: '',
  certNo: '',
  status: 1
})

async function openCreate() {
  await loadBase()
  editing.value = false
  Object.assign(form, {
    id: null,
    farmName: '',
    villageId: '',
    farmerId: '',
    address: '',
    area: '',
    soilType: '',
    waterSource: '',
    certNo: '',
    status: 1
  })
  formVisible.value = true
}

async function openEdit(f) {
  await loadBase()
  editing.value = true
  Object.assign(form, {
    id: f.id,
    farmName: f.farmName || '',
    villageId: f.villageId || '',
    farmerId: f.farmerId || '',
    address: f.address || '',
    area: f.area || '',
    soilType: f.soilType || '',
    waterSource: f.waterSource || '',
    certNo: f.certNo || '',
    status: f.status === 0 ? 0 : 1
  })
  formVisible.value = true
}

async function submit() {
  if (submitting.value) return
  if (!form.farmName.trim()) return toast('请填写农场名称')
  if (!form.villageId) return toast('请选择所属行政村')

  const payload = {
    farmName: form.farmName.trim(),
    villageId: form.villageId,
    address: form.address,
    soilType: form.soilType,
    waterSource: form.waterSource,
    certNo: form.certNo,
    status: form.status
  }
  if (form.farmerId) payload.farmerId = form.farmerId
  if (form.area) payload.area = Number(form.area)

  submitting.value = true
  try {
    if (editing.value) await updateFarm(form.id, payload)
    else await createFarm(payload)
    uni.showToast({ title: editing.value ? '已保存' : '创建成功', icon: 'success' })
    formVisible.value = false
    load()
  } catch (e) {
    /* 已提示 */
  } finally {
    submitting.value = false
  }
}

onShow(() => {
  load()
  loadBase()
})
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.w-code { flex: 1.3; }
.w-name { flex: 1.6; }
.w-addr { flex: 1.8; }
.w-act { flex: 0.7; }
.mono {
  font-family: Consolas, monospace;
  font-size: 11.5px;
}
</style>
