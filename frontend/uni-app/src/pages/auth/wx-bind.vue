<template>
  <view class="bind">
    <view class="head">
      <view class="icon">✆</view>
      <text class="title">绑定已有账号</text>
      <text class="desc">首次使用微信登录，需要绑定平台已有的账号，绑定后数据与账号密码登录完全一致。</text>
    </view>

    <view class="group">
      <view class="row">
        <text class="label">账号</text>
        <input v-model="form.username" class="input" placeholder="请输入平台账号" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">密码</text>
        <input v-model="form.password" class="input" password placeholder="请输入密码" placeholder-class="ph" />
      </view>
    </view>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '绑定中…' : '绑定并登录' }}
      </view>
      <text class="back-link" @click="toAccountLogin">返回账号密码登录</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { useForm } from '@/composables/useForm'
import { toast } from '@/utils/ui'
import { relaunch } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const store = useUserStore()
const ticket = ref('')
const form = reactive({ username: '', password: '' })

const { submitting, submit } = useForm(
  (data) => store.wxBind({ bindTicket: ticket.value, username: data.username, password: data.password }),
  {
    successText: '绑定成功',
    validate: (d) => {
      if (!d.username) return '请输入账号'
      if (!d.password) return '请输入密码'
      if (!ticket.value) return '绑定票据已失效，请重新微信登录'
      return ''
    },
    onSuccess: () => {
      setTimeout(() => relaunch(ROUTES.HOME), 600)
    }
  }
)

onLoad((opt) => {
  ticket.value = opt && opt.ticket ? decodeURIComponent(opt.ticket) : ''
  if (!ticket.value) toast('绑定票据缺失，请返回重新登录')
})

function onSubmit() {
  submit(form)
}

function toAccountLogin() {
  uni.reLaunch({ url: ROUTES.LOGIN })
}
</script>

<style lang="scss" scoped>
.bind {
  min-height: 100vh;
  background: $bg-page;
  padding: 48rpx 32rpx;
}
.head {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40rpx;
}
.icon {
  font-size: 88rpx;
  color: #22c55e;
  line-height: 1;
}
.title {
  margin-top: 20rpx;
  font-size: 36rpx;
  font-weight: 700;
  color: $text-1;
}
.desc {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: $text-3;
  text-align: center;
  line-height: 1.7;
}
.group {
  background: #fff;
  border-radius: $radius;
  padding: 0 24rpx;
  box-shadow: $shadow-card;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-height: 100rpx;
  border-bottom: 1px solid $border;
}
.row:last-child {
  border-bottom: none;
}
.label {
  width: 120rpx;
  font-size: 28rpx;
  color: $text-2;
}
.input {
  flex: 1;
  font-size: 28rpx;
}
.ph {
  color: $text-4;
}
.actions {
  margin-top: 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.btn {
  width: 100%;
  height: 92rpx;
  border-radius: 46rpx;
  background: $brand-grad;
  color: #fff;
  font-size: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn.disabled {
  opacity: 0.6;
}
.back-link {
  margin-top: 28rpx;
  font-size: 24rpx;
  color: $brand;
}
</style>
