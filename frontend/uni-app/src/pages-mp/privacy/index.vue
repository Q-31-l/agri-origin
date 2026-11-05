<template>
  <view class="pv-page">
    <view class="head">
      <text class="h-title">隐私保护指引</text>
      <text class="h-sub">农安链农产品溯源平台 · 更新于 2025 年</text>
    </view>

    <view class="card">
      <text class="intro">
        我们深知个人信息对你的重要性。本指引说明本小程序在提供溯源服务过程中会收集哪些信息、如何使用与保护，
        以及你可以如何管理这些信息。请在使用前仔细阅读。
      </text>
    </view>

    <view v-for="(s, i) in sections" :key="i" class="card">
      <text class="s-title">{{ s.title }}</text>
      <text v-for="(p, j) in s.items" :key="j" class="s-line">{{ p }}</text>
    </view>

    <view class="card contact">
      <text class="s-title">联系我们</text>
      <text class="s-line">如对本指引有任何疑问，可通过平台管理员或所属行政村联系项目组。</text>
      <text class="s-line">本指引自发布之日起生效。</text>
    </view>
  </view>
</template>

<script setup>
const sections = [
  {
    title: '一、我们收集的信息',
    items: [
      '1. 账号信息：用户名、真实姓名、手机号、所属组织与角色（由管理员在后台创建）。',
      '2. 业务信息：你录入的批次、农事记录、采收记录、检测报告、物流与销售记录。',
      '3. 设备信息：为适配界面与排查故障，可能获取设备型号、操作系统版本与网络类型。',
      '4. 位置信息：仅在你主动录入农事记录并点击「获取定位」时采集，用于标记地块位置。',
      '5. 图片与文件：仅在你主动上传现场照片或检测报告时读取所选文件。'
    ]
  },
  {
    title: '二、信息的使用',
    items: [
      '1. 用于生成批次台账、溯源履历与链上存证，实现「从田间到餐桌」的可信追溯。',
      '2. 用于按角色进行数据隔离：农户仅可查看本人批次，村级管理员仅可查看本村批次。',
      '3. 用于异常预警与状态通知（需你另行授权订阅消息）。',
      '4. 我们不会将你的个人信息用于广告推送或出售给任何第三方。'
    ]
  },
  {
    title: '三、信息的存储与保护',
    items: [
      '1. 业务数据存储于平台自建服务器，访问需通过 JWT 身份认证与角色权限校验。',
      '2. 关键业务节点的数据摘要（哈希）会写入 FISCO-BCOS 联盟链，链上仅存哈希值，不含明文个人信息。',
      '3. 质检报告等文件以哈希方式校验完整性，任何修改都会导致哈希不一致并可被检出。'
    ]
  },
  {
    title: '四、你的权利',
    items: [
      '1. 你可以在「我的」中查看并修改个人资料，可联系管理员更正错误信息。',
      '2. 你可以拒绝授权定位与相册，但这会导致相应功能（地块定位、照片上传）不可用。',
      '3. 你可以在微信「设置 — 小程序 — 农安链溯源」中管理或撤回已授予的权限。'
    ]
  },
  {
    title: '五、第三方服务',
    items: [
      '1. 微信登录：使用微信开放能力获取临时登录凭证 code，用于账号绑定，不获取你的微信昵称与头像以外的信息。',
      '2. 本平台未接入任何广告、统计分析类第三方 SDK。'
    ]
  }
]
</script>

<style lang="scss" scoped>
.pv-page {
  min-height: 100vh;
  background: #f4f7f5;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}
.head {
  padding: 16rpx 12rpx 24rpx;
}
.h-title {
  font-size: 40rpx;
  font-weight: 600;
  color: #1f2937;
}
.h-sub {
  display: block;
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 8rpx;
}
.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.intro {
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.8;
}
.s-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4rpx;
}
.s-line {
  font-size: 24rpx;
  color: #4b5563;
  line-height: 1.8;
}
.contact {
  background: #e8f5ee;
}
.contact .s-title {
  color: #1f7a4d;
}
</style>
