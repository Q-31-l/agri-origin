<template>
  <view class="ab-page">
    <view class="brand">
      <brand-logo size="lg" tone="dark" stack :sub="`v${version}`" />
      <text class="slogan">基于联盟链的农产品全流程可信溯源</text>
    </view>

    <view class="card">
      <text class="s-title">平台简介</text>
      <text class="s-line">
        本平台面向农业生产主体、检测机构、物流企业、销售商与监管部门，围绕农产品「从田间到餐桌」的全生命周期，
        记录种植、采收、质检、运输、销售与异常处置等关键节点，并将数据摘要写入 FISCO-BCOS 联盟链，
        实现过程可查、责任可追、数据不可篡改。消费者扫码即可查看完整履历。
      </text>
    </view>

    <view class="card">
      <view class="row">
        <text class="k">应用版本</text>
        <text class="v">v{{ version }}</text>
      </view>
      <view class="row">
        <text class="k">技术架构</text>
        <text class="v">uni-app + Spring Boot + FISCO-BCOS</text>
      </view>
      <view class="row">
        <text class="k">存证合约</text>
        <text class="v">{{ contractAddress || '演示模式（未启用）' }}</text>
      </view>
      <view class="row last">
        <text class="k">链服务状态</text>
        <text class="v" :class="chainOn ? 'ok' : 'warn'">{{ chainOn ? '已连接' : '演示模式' }}</text>
      </view>
    </view>

    <view class="card">
      <text class="s-title">核心能力</text>
      <view v-for="(f, i) in features" :key="i" class="feat">
        <text class="f-dot">·</text>
        <view class="f-body">
          <text class="f-title">{{ f.title }}</text>
          <text class="f-desc">{{ f.desc }}</text>
        </view>
      </view>
    </view>

    <view class="links">
      <text class="link" @click="toPrivacy">隐私保护指引</text>
      <text class="divider">|</text>
      <text class="link" @click="toPermission">权限说明</text>
    </view>

    <text class="copy">© 2025 农安链农产品溯源平台 · 毕业设计项目</text>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ENV } from '@/config/env'
import { chainStatus } from '@/api'
import { nav } from '@/utils/router'
import BrandLogo from '@/components/common/brand-logo.vue'

const version = ref(ENV.version || '1.0.0')
const chainOn = ref(false)
const contractAddress = ref('')

const features = [
  { title: '全流程留痕', desc: '种植、采收、检测、物流、销售、处置六大环节全覆盖' },
  { title: '链上存证', desc: '关键数据哈希写入联盟链，链上链下可一键校验一致性' },
  { title: '异常熔断', desc: '异常批次自动拦截物流发运与销售登记，防止问题产品流入市场' },
  { title: '角色数据隔离', desc: '农户、村级管理员、监管部门按角色自动收窄可见数据范围' },
  { title: '消费者溯源', desc: '扫码或输入溯源码，免登录查看完整履历与检测报告' }
]

function toPrivacy() {
  nav('/pages-mp/privacy/index')
}

function toPermission() {
  nav('/pages-app/permission/index')
}

onLoad(async () => {
  try {
    const res = await chainStatus()
    const d = res.data || {}
    chainOn.value = !!d.enabled
    contractAddress.value = d.contractAddress || ''
  } catch (e) {
    chainOn.value = false
  }
})
</script>

<style lang="scss" scoped>
.ab-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 40rpx 24rpx 60rpx;
  box-sizing: border-box;
}
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0 44rpx;
}
.slogan {
  font-size: 23rpx;
  color: #64748b;
  margin-top: 12rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 8rpx 28rpx;
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
}
.s-title {
  font-size: 27rpx;
  font-weight: 600;
  color: #1f2937;
  padding: 24rpx 0 12rpx;
}
.s-line {
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.85;
  padding-bottom: 24rpx;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.row.last {
  border-bottom: none;
}
.k {
  font-size: 25rpx;
  color: #64748b;
  flex-shrink: 0;
  margin-right: 20rpx;
}
.v {
  font-size: 24rpx;
  color: #1f2937;
  text-align: right;
  word-break: break-all;
}
.v.ok {
  color: #16a34a;
}
.v.warn {
  color: #b45309;
}
.feat {
  display: flex;
  flex-direction: row;
  padding-bottom: 18rpx;
}
.f-dot {
  font-size: 26rpx;
  color: #1f7a4d;
  margin-right: 10rpx;
  line-height: 1.4;
}
.f-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.f-title {
  font-size: 25rpx;
  color: #1f2937;
  font-weight: 500;
}
.f-desc {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 4rpx;
  line-height: 1.6;
}
.card .feat:last-child {
  padding-bottom: 24rpx;
}
.links {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 12rpx 0 24rpx;
}
.link {
  font-size: 23rpx;
  color: #1f7a4d;
}
.divider {
  font-size: 20rpx;
  color: #cbd5e1;
}
.copy {
  display: block;
  text-align: center;
  font-size: 21rpx;
  color: #b6bec9;
  line-height: 1.6;
}
</style>
