<!-- src/components/dao/ProposalCard.vue -->
<template>
  <el-card class="proposal-card" shadow="hover" @click="handleClick">
    <template #header>
      <div class="card-header">
        <span class="proposal-id">#{{ proposal.id }}</span>
        <StatusTag :status="proposal.state" use-dao-state />
      </div>
    </template>

    <div class="proposal-body">
      <h4 class="proposal-title">{{ proposal.description }}</h4>

      <div class="proposal-info">
        <div class="info-item">
          <el-icon><Clock /></el-icon>
          <span>{{ formatTimeRemaining(proposal.deadline) }}</span>
        </div>
        <div class="info-item">
          <el-icon><User /></el-icon>
          <span>{{ formatAddress(proposal.proposer) }}</span>
        </div>
      </div>

      <div class="vote-progress" v-if="showVotes">
        <el-progress
            :percentage="yesPercentage"
            :status="proposal.state === 'Active' ? undefined : 'success'"
            color="#67C23A"
        />
        <div class="vote-details">
          <span class="yes">赞成：{{ formatVotes(proposal.yesVotes) }}</span>
          <span class="no">反对：{{ formatVotes(proposal.noVotes) }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Clock, User } from '@element-plus/icons-vue'
import StatusTag from '@/components/common/StatusTag.vue'

const props = defineProps({
  proposal: {
    type: Object,
    required: true
  },
  showVotes: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['click'])

const yesPercentage = computed(() => {
  const yes = Number(props.proposal.yesVotes) || 0
  const no = Number(props.proposal.noVotes) || 0
  const total = yes + no
  if (total === 0) return 0
  return Math.round((yes / total) * 100)
})

function formatVotes(votes) {
  return (votes / 10**6).toFixed(2)
}

function formatAddress(address) {
  if (!address) return ''
  return `${address.slice(0, 10)}...${address.slice(-8)}`
}

function formatTimeRemaining(deadline) {
  if (!deadline) return ''
  const now = Math.floor(Date.now() / 1000)
  const diff = deadline - now

  if (diff <= 0) return '已结束'

  const days = Math.floor(diff / 86400)
  const hours = Math.floor((diff % 86400) / 3600)
  return `${days}天${hours}小时`
}

function handleClick() {
  emit('click', props.proposal)
}
</script>

<style lang="scss" scoped>
.proposal-card {
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .proposal-id {
      font-weight: 600;
      color: #606266;
    }
  }

  .proposal-body {
    .proposal-title {
      font-size: 16px;
      margin: 0 0 12px 0;
      line-height: 1.5;
    }

    .proposal-info {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
      color: #909399;
      font-size: 14px;

      .info-item {
        display: flex;
        align-items: center;
        gap: 6px;
      }
    }

    .vote-progress {
      .vote-details {
        display: flex;
        justify-content: space-between;
        margin-top: 8px;
        font-size: 12px;
        color: #606266;

        .yes {
          color: #67C23A;
        }

        .no {
          color: #F56C6C;
        }
      }
    }
  }
}
</style>
