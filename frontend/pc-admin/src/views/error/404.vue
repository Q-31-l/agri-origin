<template>
  <div class="err">
    <div class="code">404</div>
    <h1>页面不存在</h1>
    <p>访问的页面可能已被移除，或链接地址有误。</p>
    <div class="actions">
      <el-button type="primary" @click="$router.replace('/dashboard')">返回看板</el-button>
    </div>
  </div>
</template>

<style scoped>
.err {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f4f7f9;
  text-align: center;
  padding: 24px;
}

.code {
  font-size: 110px;
  font-weight: 800;
  color: #d9e6df;
  letter-spacing: 6px;
  line-height: 1;
}

h1 {
  font-size: 24px;
  margin: 16px 0 10px;
}

p {
  color: #6b7c8f;
  font-size: 14px;
}

.actions {
  margin-top: 26px;
}
</style>
