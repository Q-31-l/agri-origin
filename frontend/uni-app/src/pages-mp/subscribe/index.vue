<template>
  <view class="sub-page">
    <view class="hero">
      <text class="h-title">消息订阅</text>
      <text class="h-sub">开启后，批次状态变更、异常预警与检测结果会通过微信服务通知推送给你</text>
    </view>

    <view class="card">
      <view v-for="(it, i) in items" :key="it.key" class="row" :class="{ last: i === items.length - 1 }">
        <view class="col">
          <text class="r-title">{{ it.title }}</text>
          <text class="r-desc">{{ it.desc }}</text>
        </view>
        <switch
          :checked="!!state[it.key]"
          color="#1f7a4d"
          style="transform: scale(0.85)"
          @change="(e) => onToggle(it, e)"
        />
      </view>
    </view>

    <view class="card tip-card">
      <text class="t-title">说明</text>
      <text class="t-line">· 微信小程序订阅消息为「一次性授权」，每次推送前会再次征求你的同意</text>
      <text class="t-line">· 若曾点击「拒绝」，可点击下方按钮重新发起授权</text>
      <text class="t-line">· 订阅状态仅保存在本机，卸载或清缓存后需重新开启</text>
    </view>

    <view class="actions">
      <view class="btn" @click="requestAll">一键开启订阅</view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { toast } from '@/utils/ui'

/** 模板 ID 需在微信公众平台「订阅消息」中申请后替换 */
const TEMPLATE_IDS = {
  batchStatus: '',
  abnormal: '',
  quality: ''
}

const items = [
  {
    key: 'batchStatus',
    title: '批次状态变更',
    desc: '采收、检测、发运、销售等节点推进时提醒',
    tmplId: TEMPLATE_IDS.batchStatus
  },
  {
    key: 'abnormal',
    title: '异常预警',
    desc: '批次被标记异常或熔断时立即通知',
    tmplId: TEMPLATE_IDS.abnormal
  },
  {
    key: 'quality',
    title: '检测结果通知',
    desc: '质检报告出具后推送合格 / 不合格结论',
    tmplId: TEMPLATE_IDS.quality
  }
]

const state = reactive({
  batchStatus: false,
  abnormal: false,
  quality: false
})

function doRequest(tmplIds) {
  return new Promise((resolve) => {
    if (!tmplIds.length) {
      // 未配置模板 ID 时，仅保存本地开关，便于演示
      resolve(true)
      return
    }
    uni.requestSubscribeMessage({
      tmplIds,
      success: (res) => {
        const accepted = tmplIds.filter((id) => res[id] === 'accept')
        resolve(accepted.length > 0)
      },
      fail: () => resolve(false)
    })
  })
}

async function onToggle(it, e) {
  const on = e.detail.value
  if (!on) {
    state[it.key] = false
    return
  }
  const ok = await doRequest(it.tmplId ? [it.tmplId] : [])
  state[it.key] = ok
  if (!ok) toast('已取消授权')
}

async function requestAll() {
  const ids = items.map((i) => i.tmplId).filter(Boolean)
  const ok = ids.length ? await doRequest(ids) : true
  if (ok) {
    items.forEach((i) => {
      state[i.key] = true
    })
    uni.showToast({ title: '已开启', icon: 'success' })
  } else {
    toast('未获得订阅授权')
  }
}
</script>

<style lang="scss" scoped>
.sub-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}
.hero {
  padding: 12rpx 12rpx 28rpx;
}
.h-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #1f2937;
}
.h-sub {
  display: block;
  font-size: 24rpx;
  color: #94a3b8;
  line-height: 1.6;
  margin-top: 10rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 0 28rpx;
  margin-bottom: 20rpx;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.row.last {
  border-bottom: none;
}
.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding-right: 20rpx;
}
.r-title {
  font-size: 28rpx;
  color: #1f2937;
}
.r-desc {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 6rpx;
  line-height: 1.5;
}
.tip-card {
  padding: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.t-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #1f2937;
}
.t-line {
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.7;
}
.actions {
  margin-top: 12rpx;
}
.btn {
  height: 90rpx;
  border-radius: 14rpx;
  background: #1f7a4d;
  color: #fff;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
