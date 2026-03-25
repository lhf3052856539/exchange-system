<template>
  <div class="page-container">
    <div class="page-header" v-if="title || $slots.header">
      <slot name="header">
        <h2 class="page-title">{{ title }}</h2>
        <div class="page-actions">
          <slot name="actions"></slot>
        </div>
      </slot>
    </div>

    <el-card class="page-card" :shadow="shadow" :body-style="bodyStyle">
      <slot></slot>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: ''
  },
  shadow: {
    type: String,
    default: 'never'
  },
  bodyStyle: {
    type: Object,
    default: () => ({})
  }
})
</script>

<style lang="scss" scoped>
.page-container {
  .page-header {
    display: flex;
    justify-content: center;
    align-items: center;
    margin-bottom: 20px;
    position: relative;

    .page-title {
      font-size: 20px;
      font-weight: bold;
      color: #303133;
      margin: 0;
      text-align: center;
      flex: 1;
    }

    .page-actions {
      display: flex;
      gap: 12px;
      position: absolute;
      right: 0;
    }
  }

  .page-card {
    border-radius: 8px;
  }
}
</style>
