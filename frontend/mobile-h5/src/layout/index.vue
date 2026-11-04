<template>
  <div class="page">
    <router-view />
    <van-tabbar route fixed placeholder safe-area-inset-bottom active-color="#2f9e63" inactive-color="#8b9aaa">
      <van-tabbar-item replace to="/home" icon="wap-home-o">工作台</van-tabbar-item>
      <van-tabbar-item replace to="/batches" icon="orders-o">批次</van-tabbar-item>
      <van-tabbar-item replace to="/scan" icon="scan">溯源</van-tabbar-item>
      <van-tabbar-item replace to="/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
</script>
