<!-- src/components/common/LoadingOverlay.vue -->
<template>
  <transition name="fade">
    <div v-if="visible" class="loading-overlay">
      <div class="loading-content">
        <el-icon class="is-loading" :size="50">
          <Loading />
        </el-icon>
        <p v-if="message" class="loading-text">{{ message }}</p>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { Loading } from '@element-plus/icons-vue'

defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  message: {
    type: String,
    default: '加载中...'
  }
})
</script>

<style lang="scss" scoped>
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;

  .loading-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    color: #fff;

    .loading-text {
      font-size: 14px;
      margin: 0;
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
