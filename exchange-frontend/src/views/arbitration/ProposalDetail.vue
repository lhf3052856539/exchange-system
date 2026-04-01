<template>
  <div class="proposal-detail-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>仲裁提案详情</span>
          <el-button @click="handleBack" icon="ArrowLeft">返回</el-button>
        </div>
      </template>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-container">
        <el-alert :title="error" type="error" show-icon :closable="false" />
        <div class="error-actions">
          <el-button type="primary" @click="loadProposalDetail">重试</el-button>
        </div>
      </div>

      <template v-else-if="proposal">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="large">
          <el-descriptions-item label="提案 ID">{{ proposal.proposalId }}</el-descriptions-item>
          <el-descriptions-item label="交易 ID">
            <router-link :to="`/trade/detail/${proposal.tradeId}`">
              {{ proposal.tradeId }}
            </router-link>
          </el-descriptions-item>
          <el-descriptions-item label="被指控方">
            <div class="address-link">
              {{ formatAddress(proposal.accusedParty) }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="受害方">
            <div class="address-link">
              {{ formatAddress(proposal.victimParty) }}
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="赔偿金额">
            <span class="amount-text">{{ formatAmount(proposal.compensationAmount) }} USDT</span>
          </el-descriptions-item>
          <el-descriptions-item label="投票进度">
            <el-progress
                :percentage="(proposal.voteCount / 2) * 100"
                :status="proposal.voteCount >= 2 ? 'success' : 'normal'"
            />
            <span class="vote-count">{{ proposal.voteCount }}/2 票</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="proposal.executed" type="success">已执行</el-tag>
            <el-tag v-else-if="proposal.rejected" type="danger">已驳回</el-tag>
            <el-tag v-else-if="isExpired" type="info">已过期</el-tag>
            <el-tag v-else type="warning">投票中</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="截止时间">
            {{ formatTimestamp(proposal.deadline) }}
            <span v-if="countdownText" class="countdown">({{ countdownText }})</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 仲裁原因 -->
        <el-card shadow="never" class="mt-4">
          <template #header>
            <span class="section-title">仲裁原因</span>
          </template>
          <div class="reason-content">
            {{ proposal.reason }}
          </div>
        </el-card>

        <!-- 投票按钮（仅委员会成员） -->
        <div v-if="isCommitteeMember && !proposal.executed && !proposal.rejected && !isExpired" class="action-buttons mt-4">
          <el-button
              type="success"
              size="large"
              :loading="voting && voteSupport"
              @click="handleVote(true)"
          >
            投赞成票
          </el-button>
          <el-button
              type="danger"
              size="large"
              :loading="voting && !voteSupport"
              @click="handleVote(false)"
          >
            投反对票
          </el-button>
        </div>

        <!-- 执行按钮（仅委员会成员，且票数足够时） -->
        <div v-if="isCommitteeMember && proposal.voteCount >= 2 && !proposal.executed" class="mt-4">
          <el-button
              type="primary"
              size="large"
              :loading="executing"
              @click="handleExecute"
          >
            执行裁决
          </el-button>
        </div>
      </template>
    </el-card>

    <!-- 投票确认对话框 -->
    <el-dialog
        v-model="voteDialogVisible"
        :title="voteSupport ? '投赞成票' : '投反对票'"
        width="500px"
    >
      <el-alert
          :title="voteSupport ? '支持该提案' : '反对该提案'"
          :type="voteSupport ? 'success' : 'warning'"
          :closable="false"
      >
        <p>您确定要{{ voteSupport ? '支持' : '反对' }}这个仲裁提案吗？</p>
        <p v-if="voteSupport" class="mt-2 text-sm text-gray-600">
          如果达到 2 票支持，系统将自动执行：拉黑被指控方 + 从 Treasure 金库赔偿受害方
        </p>
      </el-alert>

      <template #footer>
        <el-button @click="voteDialogVisible = false">取消</el-button>
        <el-button
            :type="voteSupport ? 'success' : 'danger'"
            :loading="voting"
            @click="confirmVote"
        >
          确认投票
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProposalDetail, voteProposal, executeProposal } from '@/api/arbitration'
import { useWalletStore } from '@/stores'
import { formatAddress, formatAmount, formatTimestamp } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const walletStore = useWalletStore()

// 状态管理
const loading = ref(false)
const error = ref('')
const voting = ref(false)
const executing = ref(false)
const voteDialogVisible = ref(false)
const voteSupport = ref(true)
const proposal = ref(null)
const countdown = ref(0)
let countdownTimer = null

// 计算是否为委员会成员
const isCommitteeMember = computed(() => {
  if (!walletStore.address || !proposal.value) return false
  // 这里简化处理，实际需要从委员会成员列表判断
  return true
})

// 计算是否过期
const isExpired = computed(() => {
  if (!proposal.value?.deadline) return false
  return Date.now() / 1000 > proposal.value.deadline
})

// 倒计时文本
const countdownText = computed(() => {
  if (!proposal.value?.deadline || isExpired.value) return ''

  const diff = Math.floor(proposal.value.deadline - Date.now() / 1000)
  if (diff <= 0) return '已过期'

  const days = Math.floor(diff / 86400)
  const hours = Math.floor((diff % 86400) / 3600)
  const minutes = Math.floor((diff % 3600) / 60)

  if (days > 0) return `${days}天${hours}小时`
  if (hours > 0) return `${hours}小时${minutes}分钟`
  return `${minutes}分钟`
})

// 加载提案详情
async function loadProposalDetail() {
  loading.value = true
  error.value = ''

  try {
    const proposalId = route.params.proposalId
    console.log('📋 加载提案详情，proposalId:', proposalId, 'type:', typeof proposalId)

    if (!proposalId || proposalId === 'null' || proposalId === 'undefined') {
      throw new Error('提案 ID 无效：' + proposalId)
    }

    const res = await getProposalDetail(proposalId)
    console.log('📨 提案详情完整响应:', res)

    // 兼容多种响应格式
    if (res && res.data) {
      proposal.value = res.data
    } else if (res && typeof res === 'object' && !res.code) {
      // 如果返回的直接是对象而不是标准响应格式
      proposal.value = res
    } else if (res && res.code === 200 && res.data) {
      proposal.value = res.data
    } else {
      throw new Error('响应数据格式异常')
    }

    console.log('✅ 提案数据已加载:', proposal.value)
  } catch (err) {
    console.error('❌ Failed to load proposal detail:', err)
    console.error('错误详情:', err.message)
    error.value = err.message || '加载失败，请检查提案 ID 是否正确'
  } finally {
    loading.value = false
  }
}



// 处理投票
function handleVote(support) {
  voteSupport.value = support
  voteDialogVisible.value = true
}

// 确认投票
async function confirmVote() {
  voting.value = true

  try {
    await voteProposal(route.params.proposalId, voteSupport.value)
    ElMessage.success('投票成功')
    voteDialogVisible.value = false
    await loadProposalDetail()
  } catch (err) {
    ElMessage.error(err.message || '投票失败')
  } finally {
    voting.value = false
  }
}

// 执行提案
async function handleExecute() {
  executing.value = true

  try {
    await executeProposal(route.params.proposalId)
    ElMessage.success('执行成功')
    await loadProposalDetail()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executing.value = false
  }
}

// 返回
function handleBack() {
  router.back()
}

// 更新倒计时
function updateCountdown() {
  if (proposal.value?.deadline && !isExpired.value) {
    countdown.value = proposal.value.deadline - Date.now() / 1000
  }
}

onMounted(() => {
  loadProposalDetail()
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped lang="scss">
.proposal-detail-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

.address-link {
  font-family: monospace;
  color: #409eff;
}

.amount-text {
  font-size: 18px;
  font-weight: 600;
  color: #67c23a;
}

.vote-count {
  margin-left: 8px;
  font-size: 14px;
  color: #666;
}

.countdown {
  color: #f56c6c;
  font-size: 14px;
}

.reason-content {
  line-height: 1.8;
  color: #333;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.loading-container,
.error-container {
  padding: 40px 0;
}

.error-actions {
  margin-top: 16px;
  text-align: center;
}
</style>
