<!-- src/App.vue -->
<template>
  <el-config-provider :locale="locale" :size="size">
    <router-view v-slot="{ Component }">
      <transition name="router-fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </el-config-provider>
</template>

<script setup>
import { ref } from 'vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

const locale = zhCn
const size = ref('default')
</script>

<style lang="scss">/* 全局：路由过渡动画 */
.router-fade-enter-active,
.router-fade-leave-active {
  transition: all 0.3s ease;
}

.router-fade-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.router-fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>

<style lang="scss" scoped>/* 组件级：App 容器样式 */
#app {
  width: 100%;
  height: 100vh;
}
</style>

<style lang="scss">
.router-fade-enter-active,
.router-fade-leave-active {
  transition: all 0.3s ease;
}

.router-fade-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.router-fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

#app {
  width: 100%;
  height: 100vh;
}
</style>


