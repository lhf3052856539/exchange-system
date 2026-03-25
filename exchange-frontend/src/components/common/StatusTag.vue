<!-- src/components/common/StatusTag.vue -->
<template>
  <el-tag :type="tagType" :effect="effect" size="small">
    {{ statusText }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    required: true
  },
  statusMap: {
    type: Object,
    default: () => ({})
  },
  // 新增：是否使用 DAO提案状态映射
  useDaoState: {
    type: Boolean,
    default: false
  }
})

const tagType = computed(() => {
  const defaultMap = {
    PENDING: 'info',
    MATCHED: 'warning',
    CONFIRMING_A: 'primary',
    CONFIRMING_B: 'primary',
    COMPLETED: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }

  // DAO提案状态映射
  const daoMap = {
    Pending: 'info',
    Active: 'warning',
    Succeeded: 'success',
    Failed: 'danger',
    Queued: 'primary',
    Executed: 'success',
    Cancelled: 'info'
  }

  const map = props.useDaoState
      ? { ...daoMap, ...props.statusMap }
      : { ...defaultMap, ...props.statusMap }

  return map[props.status] || 'info'
})

const statusText = computed(() => {
  const defaultMap = {
    PENDING: '待匹配',
    MATCHED: '已匹配',
    CONFIRMING_A: '甲方确认中',
    CONFIRMING_B: '乙方确认中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消'
  }

  // DAO提案状态文本映射
  const daoMap = {
    Pending: '待开始',
    Active: '投票中',
    Succeeded: '已通过',
    Failed: '已失败',
    Queued: '公示期中',
    Executed: '已执行',
    Cancelled: '已取消'
  }

  const map = props.useDaoState
      ? { ...daoMap, ...props.statusMap }
      : { ...defaultMap, ...props.statusMap }

  return map[props.status] || props.status
})

const effect = 'light'
</script>
