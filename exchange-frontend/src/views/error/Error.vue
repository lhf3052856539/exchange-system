<!-- src/views/error/Error.vue -->
<template>
  <div class="error-page">
    <div class="error-content">
      <h1 class="error-code">{{ code }}</h1>
      <h2 class="error-title">{{ title }}</h2>
      <p class="error-message">{{ message }}</p>
      <el-button type="primary" @click="goHome">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const code = route.query.code || '500'
const title = route.query.title || '服务器错误'
const message = route.query.message || '抱歉，服务器发生了错误，请稍后再试'

function goHome() {
  router.push('/home')
}
</script>

<style lang="scss" scoped>
.error-page {
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
    color: #F56C6C;
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
