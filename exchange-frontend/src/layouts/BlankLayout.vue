<!-- src/layouts/BlankLayout.vue -->
<template>
  <div class="blank-layout">
    <router-view />
  </div>
</template>

<script setup>
// 空白布局，仅作为路由容器
</script>

<style lang="scss" scoped>
.blank-layout {
  min-height: 100vh;
  background: #f5f7fa;
}
</style>
