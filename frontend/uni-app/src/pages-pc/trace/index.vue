<template>
  <pc-shell title="溯源查询">
    <view class="pc-page">
      <view class="pc-head">
        <view class="col">
          <text class="pc-title">溯源查询</text>
          <text class="pc-sub">输入溯源码或批次编码，查看全流程履历与链上存证</text>
        </view>
      </view>

      <!-- 查询框 -->
      <view class="hero">
        <text class="hero-title">农产品全流程溯源</text>
        <text class="hero-sub">每一次农事、检测、运输与销售都已写入联盟链，不可篡改</text>
        <view class="search">
          <input
            class="s-input"
            v-model="code"
            :focus="inputFocus"
            placeholder="输入溯源码，或点右侧扫码"
            @confirm="doQuery"
          />
          <view class="s-scan" @click="onScan">
            <text class="s-scan-ico">⌗</text>
            <text class="s-scan-txt">扫码</text>
          </view>
          <view class="s-btn" @click="doQuery">立即查询</view>
        </view>
        <view class="samples">
          <text class="s-label">示例：</text>
          <text v-for="c in samples" :key="c" class="s-chip" @click="quick(c)">{{ c }}</text>
          <text v-if="!samples.length" class="s-label">暂无示例数据</text>
        </view>
      </view>

      <view v-if="loading" class="pc-none">查询中…</view>
      <view v-else-if="notFound" class="pc-card">
        <view class="pc-card-body">
          <view class="pc-none">
            未查询到该溯源码对应的批次，请核对后重试
          </view>
        </view>
      </view>

      <block v-else-if="data">
        <!-- 概览 -->
        <view class="pc-card">
          <view class="pc-card-body">
            <view class="ov">
              <view class="ov-left">
                <view class="ov-title-row">
                  <text class="ov-name">{{ data.productName }}</text>
                  <text v-if="data.variety" class="ov-variety">· {{ data.variety }}</text>
                  <pc-status :value="data.status" type="batch" />
                  <text v-if="data.abnormalFlag === 1" class="abn">异常</text>
                </view>
                <text class="ov-origin">{{ data.origin || data.villageName || '-' }} · {{ data.farmName || '-' }}</text>
                <view class="ov-grid">
                  <view class="ov-item"><text class="ok">批次编码</text><text class="ov mono">{{ data.batchNo }}</text></view>
                  <view class="ov-item"><text class="ok">溯源码</text><text class="ov mono">{{ data.traceCode }}</text></view>
                  <view class="ov-item"><text class="ok">农户</text><text class="ov">{{ data.farmerName || '-' }}</text></view>
                  <view class="ov-item"><text class="ok">所属村</text><text class="ov">{{ data.villageName || '-' }}</text></view>
                  <view class="ov-item"><text class="ok">种植日期</text><text class="ov">{{ data.plantingDate || '-' }}</text></view>
                  <view class="ov-item"><text class="ok">采收日期</text><text class="ov">{{ data.harvestDate || '-' }}</text></view>
                  <view class="ov-item"><text class="ok">产量</text><text class="ov">{{ formatQty(data.quantity, data.unit) }}</text></view>
                  <view class="ov-item"><text class="ok">质检结论</text><text class="ov">{{ qualityText }}</text></view>
                </view>
              </view>
              <view class="ov-right">
                <pc-qr :code="data.traceCode" label="扫码查看溯源履历" :size="140" />
              </view>
            </view>
          </view>
        </view>

        <!-- 链上存证 -->
        <view class="pc-cols">
          <view class="pc-col-main">
            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">全流程履历</text>
                <text class="mini">{{ (data.timeline || []).length }} 个节点</text>
              </view>
              <view class="pc-card-body">
                <pc-timeline :items="data.timeline || []" />
              </view>
            </view>
          </view>

          <view class="pc-col-side">
            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">链上存证</text>
                <pc-status :value="data.chainStatus" type="chain" />
              </view>
              <view class="pc-card-body">
                <view class="pc-kv">
                  <text class="pc-k">交易哈希</text>
                  <text class="pc-v mono">{{ data.chainTxHash || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">区块高度</text>
                  <text class="pc-v">{{ data.blockNumber || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">链服务</text>
                  <text class="pc-v">{{ data.chainEnabled ? '已连接' : '演示模式' }}</text>
                </view>
                <text v-if="data.chainMessage" class="chain-msg">{{ data.chainMessage }}</text>
              </view>
            </view>

            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">质检信息</text>
              </view>
              <view class="pc-card-body">
                <view class="pc-kv">
                  <text class="pc-k">报告编号</text>
                  <text class="pc-v mono">{{ data.qualityReportNo || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">检测机构</text>
                  <text class="pc-v">{{ data.qualityOrg || '-' }}</text>
                </view>
                <view class="pc-kv">
                  <text class="pc-k">检测结论</text>
                  <text class="pc-v">
                    <pc-status v-if="data.qualityResult" :value="data.qualityResult" type="quality" />
                    <text v-else>-</text>
                  </text>
                </view>
              </view>
            </view>

            <view class="pc-card">
              <view class="pc-card-head">
                <text class="pc-card-title">防伪校验</text>
              </view>
              <view class="pc-card-body">
                <view v-if="verify" class="vf" :class="verify.passed ? 'ok' : 'bad'">
                  <text class="vf-title">{{ verify.passed ? '链上数据一致' : '存在不一致' }}</text>
                  <text class="vf-msg">{{ verify.message }}</text>
                  <text class="vf-meta">{{ verify.matchedRecords }}/{{ verify.totalRecords }} 条一致</text>
                </view>
                <view v-else class="pc-none small">暂无校验结果</view>
              </view>
            </view>
          </view>
        </view>
      </block>

      <view v-else class="pc-card">
        <view class="pc-card-body">
          <view class="empty-hero">
            <text class="eh-icon">⌕</text>
            <text class="eh-text">输入溯源码开始查询</text>
            <text class="eh-sub">溯源数据对所有消费者公开，无需登录</text>
          </view>
        </view>
      </view>
    </view>
  </pc-shell>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcShell from '@/layouts/pc-shell.vue'
import PcStatus from '@/components/pc/pc-status.vue'
import PcQr from '@/components/pc/pc-qr.vue'
import PcTimeline from '@/components/pc/pc-timeline.vue'
import { traceQuery, traceVerify, traceSamples } from '@/api'
import { QUALITY_RESULT } from '@/config/constants'
import { toast } from '@/utils/ui'
import { formatQty } from '@/utils/format'
import { scan, extractTraceCode } from '@/utils/scan'

const code = ref('')
const data = ref(null)
const verify = ref(null)
const loading = ref(false)
const notFound = ref(false)
const samples = ref([])
const inputFocus = ref(false)

const qualityText = computed(() => {
  const r = data.value && data.value.qualityResult
  const c = QUALITY_RESULT[r]
  return c ? c.text : r || '-'
})

async function doQuery() {
  const c = code.value.trim()
  if (!c) return toast('请输入溯源码')
  loading.value = true
  notFound.value = false
  data.value = null
  verify.value = null
  try {
    const res = await traceQuery(c)
    data.value = res.data || null
    if (!data.value) notFound.value = true
    else loadVerify(c)
  } catch (e) {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function loadVerify(c) {
  try {
    verify.value = (await traceVerify(c)).data || null
  } catch (e) {
    verify.value = null
  }
}

function quick(c) {
  code.value = c
  doQuery()
}

/** PC 上通常没有摄像头：扫码层会自动退到「上传二维码图片识别」，同样能读码 */
async function onScan() {
  const raw = await scan({ title: '扫描或上传溯源码' })
  if (!raw) {
    inputFocus.value = false
    setTimeout(() => (inputFocus.value = true), 60)
    return
  }
  const c = extractTraceCode(raw)
  if (!c) return toast('未识别到有效溯源码')
  code.value = c
  doQuery()
}

async function loadSamples() {
  try {
    const res = await traceSamples(3)
    samples.value = (res.data || []).filter(Boolean)
  } catch (e) {
    samples.value = []
  }
}

onLoad((opt) => {
  if (opt && opt.code) {
    code.value = decodeURIComponent(opt.code)
    doQuery()
  }
  loadSamples()
})
</script>

<style lang="scss" scoped>
.col {
  display: flex;
  flex-direction: column;
}
.hero {
  background: linear-gradient(135deg, #14432c 0%, #1f7a4d 100%);
  border-radius: 12px;
  padding: 30px 32px 26px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.hero-title {
  font-size: 21px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5px;
}
.hero-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  margin-top: 7px;
}
.search {
  display: flex;
  flex-direction: row;
  gap: 0;
  margin-top: 18px;
  width: 100%;
  max-width: 560px;
}
.s-input {
  flex: 1;
  height: 40px;
  background: #fff;
  border-radius: 8px 0 0 8px;
  padding: 0 14px;
  font-size: 13px;
  color: #1f2937;
  box-sizing: border-box;
}
.s-input .uni-input-input {
  height: 100%;
  font-size: 13px;
}
.s-btn {
  height: 40px;
  padding: 0 22px;
  background: #f0a92c;
  color: #3d2606;
  font-size: 13px;
  font-weight: 600;
  border-radius: 0 8px 8px 0;
  display: flex;
  align-items: center;
  cursor: pointer;
}
.s-btn:hover {
  background: #e09b1c;
}
.s-scan {
  height: 40px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-left: none;
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  white-space: nowrap;
  box-sizing: border-box;
}
.s-scan:hover {
  background: rgba(255, 255, 255, 0.26);
}
.s-scan-ico {
  font-size: 15px;
  line-height: 1;
}
.s-scan-txt {
  font-size: 13px;
}
.samples {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  flex-wrap: wrap;
  justify-content: center;
}
.s-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.55);
}
.s-chip {
  font-size: 11px;
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  padding: 2px 9px;
  cursor: pointer;
  font-family: Consolas, monospace;
}
.s-chip:hover {
  background: rgba(255, 255, 255, 0.28);
}

.ov {
  display: flex;
  flex-direction: row;
  gap: 20px;
}
.ov-left {
  flex: 1;
  min-width: 0;
}
.ov-right {
  width: 170px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-left: 1px dashed #eef2f6;
  padding-left: 18px;
}
.ov-title-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ov-name {
  font-size: 19px;
  font-weight: 700;
  color: #1f2937;
}
.ov-variety {
  font-size: 12px;
  color: #94a3b8;
}
.abn {
  font-size: 10.5px;
  color: #d93026;
  background: #fdeceb;
  border-radius: 3px;
  padding: 1px 6px;
}
.ov-origin {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}
.ov-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  margin-top: 14px;
}
.ov-item {
  width: 25%;
  box-sizing: border-box;
  padding: 6px 12px 6px 0;
  display: flex;
  flex-direction: column;
}
.ok {
  font-size: 10.5px;
  color: #94a3b8;
}
.ov {
  font-size: 12px;
  color: #1f2937;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mono {
  font-family: Consolas, monospace;
}
.mini {
  font-size: 11px;
  color: #94a3b8;
}
.chain-msg {
  display: block;
  font-size: 11px;
  color: #b45309;
  background: #fef6e0;
  border-radius: 5px;
  padding: 6px 8px;
  margin-top: 8px;
  line-height: 1.5;
}
.vf {
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
}
.vf.ok {
  background: #e8f7ee;
}
.vf.bad {
  background: #fdeceb;
}
.vf-title {
  font-size: 12.5px;
  font-weight: 600;
  color: #1f2937;
}
.vf-msg {
  font-size: 11px;
  color: #4b5563;
  margin-top: 5px;
  line-height: 1.5;
}
.vf-meta {
  font-size: 10.5px;
  color: #94a3b8;
  margin-top: 4px;
}
.pc-none.small {
  padding: 20px;
  font-size: 11px;
}
.empty-hero {
  padding: 56px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.eh-icon {
  font-size: 34px;
  color: #cbd5e1;
}
.eh-text {
  font-size: 13px;
  color: #64748b;
  margin-top: 8px;
}
.eh-sub {
  font-size: 11px;
  color: #b6bec9;
  margin-top: 5px;
}
</style>
