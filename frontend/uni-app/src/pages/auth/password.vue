<template>
  <view class="pwd">
    <view class="group">
      <view class="row">
        <text class="label">原密码</text>
        <input v-model="form.oldPassword" class="input" password placeholder="请输入原密码" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">新密码</text>
        <input v-model="form.newPassword" class="input" password placeholder="至少 6 位" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">确认新密码</text>
        <input v-model="form.confirmPassword" class="input" password placeholder="再次输入新密码" placeholder-class="ph" />
      </view>
    </view>

    <text class="tip">修改成功后需要重新登录，已登录的其他端不受影响。</text>

    <view class="actions">
      <view class="btn" :class="{ disabled: submitting }" @click="onSubmit">
        {{ submitting ? '提交中…' : '确认修改' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive } from 'vue'
import { changePassword } from '@/api'
import { useUserStore } from '@/store/user'
import { useForm } from '@/composables/useForm'
import { relaunch } from '@/utils/router'
import { ROUTES } from '@/config/routes'

const store = useUserStore()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const { submitting, submit } = useForm((data) => changePassword(data), {
  successText: '密码已修改，请重新登录',
  validate: (d) => {
    if (!d.oldPassword) return '请输入原密码'
    if (!d.newPassword || d.newPassword.length < 6) return '新密码至少 6 位'
    if (d.newPassword !== d.confirmPassword) return '两次输入的新密码不一致'
    if (d.newPassword === d.oldPassword) return '新密码不能与原密码相同'
    return ''
  },
  onSuccess: () => {
    setTimeout(async () => {
      await store.logout()
      relaunch(ROUTES.LOGIN)
    }, 900)
  }
})

function onSubmit() {
  submit({
    oldPassword: form.oldPassword,
    newPassword: form.newPassword
  })
}
</script>

<style lang="scss" scoped>
.pwd {
  min-height: 100vh;
  background: $bg-page;
  padding: 24rpx;
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
  width: 200rpx;
  font-size: 28rpx;
  color: $text-2;
}
.input {
  flex: 1;
  font-size: 28rpx;
  text-align: right;
}
.ph {
  color: $text-4;
}
.tip {
  display: block;
  margin: 24rpx 8rpx;
  font-size: 22rpx;
  color: $text-3;
  line-height: 1.6;
}
.actions {
  margin-top: 40rpx;
}
.btn {
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
</style>
