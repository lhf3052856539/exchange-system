<!-- src/layouts/AuthLayout.vue -->
<template>
  <div class="auth-layout">
    <div class="auth-container">
      <div class="auth-header">
        <div class="logo" @click="$router.push('/home')">
          <img src="@/assets/images/logo.svg" alt="Logo">
          <h1>去中心化交易所</h1>
        </div>
      </div>

      <div class="auth-content">
        <router-view />
      </div>

      <div class="auth-footer">
        <p>&copy; 2024 去中心化交易所。All rights reserved.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
// 无脚本逻辑，纯展示组件
</script>

<style lang="scss" scoped>
.auth-layout {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.auth-container {
  width: 100%;
  max-width: 500px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

.auth-header {
  padding: 40px 40px 20px;
  text-align: center;

  .logo {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    cursor: pointer;

    img {
      height: 60px;
    }

    h1 {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }
}

.auth-content {
  padding: 40px;
}

.auth-footer {
  padding: 20px;
  text-align: center;
  background: #f5f7fa;

  p {
    margin: 0;
    font-size: 12px;
    color: #909399;
  }
}
</style>
