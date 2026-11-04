<template>
  <div class="err">
    <div class="code">403</div>
    <h1>越权访问已被拦截</h1>
    <p>当前角色不具备该页面的访问权限。系统已记录本次操作，如需权限请联系管理员。</p>
    <div class="actions">
      <el-button type="primary" @click="$router.replace('/dashboard')">返回看板</el-button>
      <el-button @click="$router.back()">返回上一页</el-button>
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
  max-width: 460px;
  line-height: 1.8;
}

.actions {
  margin-top: 26px;
  display: flex;
  gap: 12px;
}
</style>
