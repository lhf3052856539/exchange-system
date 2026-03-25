<!-- src/views/error/NotFound.vue -->
<template>
  <div class="not-found-page">
    <div class="error-content">
      <h1 class="error-code">404</h1>
      <h2 class="error-title">页面不存在</h2>
      <p class="error-message">
        抱歉，您访问的页面不存在或已被移除
      </p>
      <el-button type="primary" @click="goHome">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/home')
}
</script>

<style lang="scss" scoped>
.not-found-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 120px);
  background: #f5f7fa;
}

.error-content {
  text-align: center;
  padding: 40px;

  .error-code {
    font-size: 120px;
    font-weight: bold;
    color: #409EFF;
    margin: 0;
    line-height: 1;
  }

  .error-title {
    font-size: 32px;
    color: #303133;
    margin: 20px 0;
  }

  .error-message {
    font-size: 16px;
    color: #909399;
    margin-bottom: 30px;
  }
}
</style>
