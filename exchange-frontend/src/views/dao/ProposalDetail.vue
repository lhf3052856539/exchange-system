<!-- src/views/dao/ProposalDetail.vue -->
<template>
  <div class="proposal-detail-page">
        <!-- 投票权提示 -->
        <el-alert
            v-if="walletStore.isConnected && votingPower === 0"
            title="您还没有投票权"
            type="warning"
            :closable="false"
            show-icon
            class="mb-4"
        >
          <template #default>
            <p class="mt-2">
              您需要先委托投票权给自己才能参与投票。
              <el-button type="primary" size="small" @click="handleDelegate" :loading="delegating">
                立即委托
              </el-button>
            </p>
          </template>
        </el-alert>

        <el-alert
            v-else-if="walletStore.isConnected && votingPower > 0"
            title="您有投票权"
            type="success"
            :closable="false"
            show-icon
            class="mb-4"
        >
          <template #default>
            <p class="mt-2">
              当前投票权：<strong>{{ formatVotingPower(votingPower) }}</strong> EXTH
              <br>
              <span v-if="!hasVotingPowerAtSnapshot" style="color: #E6A23C; font-size: 13px;">
            ⚠️ 但您在提案创建时可能没有投票权，无法参与此提案投票
          </span>
            </p>
          </template>
        </el-alert>
    <el-card>
      <template #header>
        <div class="card-header">
          <el-button @click="$router.push('/dao')">
            <el-icon><ArrowLeft /></el-icon>
            返回列表
          </el-button>
          <el-button @click="$router.push('/')">
            <el-icon><HomeFilled /></el-icon>
            首页
          </el-button>
          <span class="page-title">提案详情 #{{ proposalId }}</span>
          <el-tag :type="getStateTag(currentProposal?.state)" size="large">
            {{ getStateText(currentProposal?.state) }}
          </el-tag>
        </div>
      </template>

      <div class="proposal-content">
        <!-- 基本信息 -->
        <el-descriptions title="基本信息" :column="2" border>
          <el-descriptions-item label="提案 ID">{{ currentProposal?.id || proposalId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStateTag(currentProposal?.state)">
              {{ getStateText(currentProposal?.state) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提出者" :span="2">
            {{ formatAddress(currentProposal?.proposer) }}
          </el-descriptions-item>
          <el-descriptions-item label="投票开始" :span="2">
            {{ formatTimestamp(currentProposal?.startTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="投票截止" :span="2">
            {{ formatTimestamp(currentProposal?.deadline) }}
          </el-descriptions-item>
          <el-descriptions-item label="剩余时间" :span="2">
            {{ getTimeRemaining(currentProposal?.deadline) }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 提案描述 -->
        <el-divider>提案详情</el-divider>
        <div class="description">
          {{ currentProposal?.description || '暂无详细描述' }}
        </div>

        <!-- 投票统计 -->
        <el-divider>投票统计</el-divider>
        <div class="vote-stats">
          <div class="vote-bar">
            <el-progress
                :percentage="yesPercentage"
                :status="currentProposal?.state === ProposalState.Active ? undefined : 'success'"
                color="#67C23A"
            />
          </div>
          <div class="vote-info">
            <span>赞成：{{ formatVotes(currentProposal?.yesVotes) }}</span>
            <span>反对：{{ formatVotes(currentProposal?.noVotes) }}</span>
          </div>
        </div>


        <!-- 操作按钮 -->
        <el-divider>操作</el-divider>
        <div class="actions">
          <el-space wrap>
            <el-button
                v-if="canVote"
                type="success"
                @click="handleVote(true)"
                :loading="processing"
            >
              投赞成票
            </el-button>
            <el-button
                v-if="canVote"
                type="danger"
                @click="handleVote(false)"
                :loading="processing"
            >
              投反对票
            </el-button>
            <el-button
                v-if="canQueue"
                type="primary"
                @click="handleQueue"
                :loading="processing"
            >
              加入公示期
            </el-button>
            <el-button
                v-if="canExecute"
                type="success"
                @click="handleExecute"
                :loading="processing"
            >
              执行提案
            </el-button>
            <el-button
                v-if="canCancel"
                type="warning"
                @click="handleCancel"
                :loading="processing"
            >
              取消提案
            </el-button>
          </el-space>
        </div>

        <!-- 目标合约信息 -->
        <el-divider>执行信息</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="目标合约">
            {{ formatAddress(currentProposal?.targetContract) }}
          </el-descriptions-item>
          <el-descriptions-item label="ETH 数量">
            {{ currentProposal?.value || 0 }} ETH
          </el-descriptions-item>
          <el-descriptions-item label="调用数据" :span="2">
            <el-input
                type="textarea"
                :model-value="currentProposal?.callData || '0x'"
                :rows="4"
                readonly
            />
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup>import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useDao } from '@/composables/useDao'
import { useWalletStore } from '@/stores'
import { ethers } from 'ethers'
import web3Util from '@/utils/web3'

const route = useRoute()
const router = useRouter()
const walletStore = useWalletStore()

const delegating = ref(false)
const votingPower = ref(0)
const hasUserVoted = ref(false)
const hasVotingPowerAtSnapshot = ref(true) // 新增：记录在提案创建时是否有投票权

const {
  currentProposal,
  loading,
  processing,
  loadProposalDetail,
  submitVote,
  submitQueue,
  submitExecute,
  submitCancel,
  getStateTag,
  getStateText,
  formatTimestamp,
  getTimeRemaining,
  formatVotes,
  checkHasVoted
} = useDao()

const proposalId = ref(route.params.id)

// ProposalState 枚举
const ProposalState = {
  Pending: 0,
  Active: 1,
  Canceled: 2,
  Defeated: 3,
  Succeeded: 4,
  Queued: 5,
  Expired: 6,
  Executed: 7
}

const yesPercentage = computed(() => {
  const yes = Number(currentProposal.value?.yesVotes) || 0
  const no = Number(currentProposal.value?.noVotes) || 0
  const total = yes + no
  if (total === 0) return 0
  return Math.round((yes / total) * 100)
})

const canVote = computed(() => {
  return currentProposal.value?.state === ProposalState.Active &&
      !hasUserVoted.value &&
      walletStore.isConnected
})

const canQueue = computed(() => {
  return currentProposal.value?.state === ProposalState.Succeeded &&
      walletStore.isConnected
})

const canExecute = computed(() => {
  return currentProposal.value?.state === ProposalState.Queued &&
      walletStore.isConnected
})

const canCancel = computed(() => {
  return (currentProposal.value?.state === ProposalState.Active ||
          currentProposal.value?.state === ProposalState.Succeeded) &&
      walletStore.isConnected
})

function formatAddress(address) {
  if (!address) return ''
  return `${address.slice(0, 10)}...${address.slice(-8)}`
}

function formatVotingPower(power) {
  return Number(power).toFixed(2)
}

async function handleVote(support) {
  try {
    // 检查是否已投票
    if (hasUserVoted.value) {
      ElMessage.warning('您已经投过票了')
      return
    }

    // 检查投票权
    if (!hasVotingPowerAtSnapshot.value) {
      ElMessage.error('您在提案创建时没有投票权，无法参与投票。请在下次创建提案前先委托投票权！')
      return
    }

    await ElMessageBox.confirm(
        `确定要${support ? '支持' : '反对'}这个提案吗？`,
        '投票确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await submitVote(proposalId.value, support)
    ElMessage.success('投票成功')
    hasUserVoted.value = true
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Vote failed:', error)

      // 检查是否是 "No voting power" 错误
      if (error.message && (error.message.includes('No voting power') || error.message.includes('voting power'))) {
        ElMessage.error('您在提案创建时没有投票权，无法参与投票！')
        hasVotingPowerAtSnapshot.value = false
      } else if (error.reason && (error.reason.includes('No voting power') || error.reason.includes('voting power'))) {
        ElMessage.error('您在提案创建时没有投票权，无法参与投票！')
        hasVotingPowerAtSnapshot.value = false
      } else {
        ElMessage.error('投票失败：' + (error.reason || error.message || '未知错误'))
      }
    }
  }
}

async function handleQueue() {
  try {
    await ElMessageBox.confirm(
        '确定要将此提案加入公示期吗？',
        '确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await submitQueue(proposalId.value)
    ElMessage.success('操作成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Queue failed:', error)
    }
  }
}

async function handleExecute() {
  try {
    const eta = currentProposal.value?.eta
    await ElMessageBox.confirm(
        '确定要执行这个提案吗？此操作不可撤销。',
        '执行确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await submitExecute(proposalId.value, eta)
    ElMessage.success('提案已执行')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Execute failed:', error)
    }
  }
}

async function handleCancel() {
  try {
    await submitCancel(proposalId.value)
    ElMessage.success('提案已取消')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Cancel failed:', error)
    }
  }
}

async function handleDelegate() {
  try {
    if (!walletStore.address) {
      ElMessage.error('请先连接钱包')
      return
    }

    delegating.value = true

    const exthAddress = import.meta.env.VITE_EXTH_CONTRACT_ADDRESS
    const contract = web3Util.getExthContract(exthAddress)

    if (!contract) {
      ElMessage.error('无法获取 EXTH 合约实例')
      return
    }

    const tx = await contract.delegate(walletStore.address)
    await tx.wait()

    ElMessage.success('投票权委托成功！')
    await fetchVotingPower()

  } catch (error) {
    console.error('Delegate failed:', error)
    if (error.reason) {
      ElMessage.error('委托失败：' + error.reason)
    } else if (error.message) {
      ElMessage.error('委托失败：' + error.message)
    }
  } finally {
    delegating.value = false
  }
}

async function fetchVotingPower() {
  try {
    if (!walletStore.address) return

    const exthAddress = import.meta.env.VITE_EXTH_CONTRACT_ADDRESS
    if (!exthAddress) {
      console.error('EXTH contract address not configured')
      return
    }

    const exthABI = [
      'function getVotes(address account) view returns (uint256)',
      'function getPastVotes(address account, uint256 blockNumber) view returns (uint256)'
    ]

    const provider = new ethers.BrowserProvider(window.ethereum)
    const contract = new ethers.Contract(exthAddress, exthABI, provider)

    // 查询当前的投票权
    const power = await contract.getVotes(walletStore.address)
    votingPower.value = ethers.formatUnits(power, 6)

    // 如果有提案信息，检查在提案创建时的投票权
    if (currentProposal.value?.snapshotBlock) {
      try {
        const pastPower = await contract.getPastVotes(walletStore.address, currentProposal.value.snapshotBlock)
        const pastPowerNum = parseFloat(ethers.formatUnits(pastPower, 6))

        if (pastPowerNum > 0) {
          hasVotingPowerAtSnapshot.value = true
        } else {
          hasVotingPowerAtSnapshot.value = false
          console.warn('用户在提案创建时没有投票权')
        }
      } catch (err) {
        console.error('Failed to get past votes:', err)
        // 如果查询历史快照失败，暂时认为有投票权（让链上检查来决定）
        hasVotingPowerAtSnapshot.value = true
      }
    }
  } catch (error) {
    console.error('Failed to fetch voting power:', error)
  }
}

onMounted(async () => {
  await loadProposalDetail(proposalId.value)

  if (walletStore.isConnected) {
    await fetchVotingPower()

    // 检查是否已投票
    try {
      const voted = await checkHasVoted(proposalId.value)
      hasUserVoted.value = voted
    } catch (error) {
      console.error('Failed to check voting status:', error)
    }
  }
})
</script>

<style lang="scss" scoped>
.proposal-detail-page {
  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .page-title {
      font-size: 18px;
      font-weight: bold;
      flex: 1;
    }
  }

  .proposal-content {
    .description {
      padding: 20px;
      background: #f5f7fa;
      border-radius: 4px;
      line-height: 1.8;
      white-space: pre-wrap;
    }

    .vote-stats {
      padding: 20px 0;

      .vote-bar {
        margin-bottom: 12px;
      }

      .vote-info {
        display: flex;
        justify-content: space-between;
        color: #606266;
        font-size: 14px;
      }
    }

    .actions {
      padding: 20px 0;
    }
  }
}
</style>
