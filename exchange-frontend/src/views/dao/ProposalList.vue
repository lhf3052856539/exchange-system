<template>
  <div class="proposal-list-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="$router.push('/')">
              <el-icon><HomeFilled /></el-icon>
              首页
            </el-button>
            <span class="page-title">DAO 治理提案</span>
          </div>
          <el-button type="primary" class="create-proposal-btn" @click="handleCreateProposal">
            <el-icon><Plus /></el-icon>
            创建提案
          </el-button>
        </div>
      </template>

      <!-- DAO 统计信息 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">总提案数</div>
            <div class="stat-value">{{ daoStatsLocal?.totalProposals || daoStats?.totalProposals || proposals.length || 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">投票中</div>
            <div class="stat-value success">
              {{ proposals.filter(p => p.state === 1).length }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">已通过</div>
            <div class="stat-value primary">
              {{ proposals.filter(p => p.state === 2).length }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">我的投票权</div>
            <div class="stat-value warning">
              {{ votingPowerLocal || votingPower || 0 }} EXTH
              <el-button
                  v-if="walletStore.isConnected && (votingPowerLocal || votingPower || 0) === 0"
                  type="primary"
                  size="small"
                  @click.stop="handleDelegate"
                  :loading="delegating"
                  class="ml-2"
              >
                委托投票权
              </el-button>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 提案列表 -->
      <el-table
          :data="proposals"
          v-loading="loading"
          style="width: 100%"
          @row-click="handleRowClick"
      >
        <el-table-column prop="proposalId" label="ID" width="80" />
        <el-table-column prop="description" label="提案描述" min-width="300" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStateTag(row.state)">
              {{ getStateText(row.state) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="赞成票" width="120">
          <template #default="{ row }">
            {{ formatVotes(row.yesVotes) }}
          </template>
        </el-table-column>
        <el-table-column label="反对票" width="120">
          <template #default="{ row }">
            {{ formatVotes(row.noVotes) }}
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.deadline) }}
          </template>
        </el-table-column>
        <el-table-column label="剩余时间" width="120">
          <template #default="{ row }">
            <span :class="{ 'text-danger': getTimeRemaining(row.deadline) === '已结束' }">
              {{ getTimeRemaining(row.deadline) }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 子路由视图容器 -->
    <router-view />
  </div>
</template>

<script setup>
  import { ref, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { ElMessage } from 'element-plus'
  import { Plus, HomeFilled } from '@element-plus/icons-vue'
  import { useDao } from '@/composables/useDao'
  import { useWalletStore } from '@/stores'
  import { ethers } from 'ethers'

  const router = useRouter()
  const walletStore = useWalletStore()
  const delegating = ref(false)
  const votingPowerLocal = ref(0)
  const daoStatsLocal = ref(null)
  const {
    proposals,
    votingPower,
    daoStats,
    loading,
    pagination,
    loadProposals,
    getStateTag,
    getStateText,
    formatTimestamp,
    getTimeRemaining
  } = useDao()


function formatVotes(votes) {
  if (!votes) return '0'
  // 假设投票数以代币最小单位存储，转换为标准单位
  return (votes / 10**6).toFixed(2)
}

async function handleCreateProposal() {
  try {
    console.log('🔍 Check wallet status:', {
      isConnected: walletStore.isConnected,
      address: walletStore.address,
      hasAddress: !!walletStore.address
    })

    if (!walletStore.isConnected || !walletStore.address) {
      ElMessage.warning('请先连接钱包')
      return
    }

    console.log('✅ Wallet verified, navigating to /dao/create...')

    const result = await router.push('/dao/create')

    console.log('✅ Navigation completed:', result)
    console.log('Current route:', router.currentRoute.value.path)

  } catch (error) {
    console.error('❌ Navigation failed:', error)
    ElMessage.error('跳转失败：' + error.message)
  }
}

async function handleDelegate() {
  try {
    if (!walletStore.address) {
      ElMessage.warning('请先连接钱包')
      return
    }

    delegating.value = true

    // 获取 EXTH 合约地址
    const exthAddress = import.meta.env.VITE_EXTH_CONTRACT_ADDRESS

    if (!exthAddress) {
      ElMessage.error('EXTH 合约地址未配置')
      return
    }

    console.log('🔐 Delegating votes to:', walletStore.address)

    // ✅ 检查并切换到正确的网络
    const provider = new ethers.BrowserProvider(window.ethereum)

    // 获取当前网络
    const network = await provider.getNetwork()
    const currentChainId = Number(network.chainId)

    // Sepolia 的 Chain ID 是 11155111
    const SEPOLIA_CHAIN_ID = 11155111

    console.log('Current chain ID:', currentChainId, 'Expected:', SEPOLIA_CHAIN_ID)

    if (currentChainId !== SEPOLIA_CHAIN_ID) {
      ElMessage.warning('检测到网络不匹配，正在切换到 Sepolia 网络...')

      try {
        await window.ethereum.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0xaa36a7' }] // 11155111 的 hex 表示
        })

        // 等待网络切换完成
        await new Promise(resolve => setTimeout(resolve, 1000))

        ElMessage.success('网络切换成功')
      } catch (switchError) {
        console.error('Failed to switch network:', switchError)
        ElMessage.error('请手动切换到 Sepolia 网络')
        throw new Error('网络切换失败')
      }
    }

    // 重新创建 signer（网络切换后需要重新获取）
    const signer = await provider.getSigner()

    // EXTH 合约的 ABI（只需要 delegate 方法）
    const exthABI = [
      'function delegate(address delegatee) external'
    ]

    // 创建合约实例
    const contract = new ethers.Contract(exthAddress, exthABI, signer)

    // 调用 delegate 方法
    const tx = await contract.delegate(walletStore.address)

    console.log('⏳ Waiting for delegation transaction...', tx.hash)

    // 等待交易确认
    const receipt = await tx.wait()

    console.log('✅ Delegation successful, tx hash:', receipt.hash)

    ElMessage.success('投票权委托成功！')

    // 延迟一下重新加载投票权
    setTimeout(() => {
      window.location.reload()
    }, 1000)

  } catch (error) {
    console.error('❌ Delegate failed:', error)
    if (error.reason) {
      ElMessage.error('委托失败：' + error.reason)
    } else if (error.message) {
      ElMessage.error('委托失败：' + error.message)
    } else {
      ElMessage.error('委托失败，请检查网络和 MetaMask 连接')
    }
  } finally {
    delegating.value = false
  }
}



  function handleRowClick(row) {
    // 使用 proposalId 而不是 id
    router.push(`/dao/${row.proposalId}`)
  }

  function calculateActiveProposals() {
    if (!proposals || !proposals.length) return 0

    // ✅ 使用 useDao 中的 getStateText 来判断
    return proposals.filter(p => {
      const stateText = getStateText(p.state)
      return stateText === '投票中'
    }).length
  }

  function calculateSucceededProposals() {
    if (!proposals || !proposals.length) return 0

    return proposals.filter(p => {
      const stateText = getStateText(p.state)
      return stateText === '已通过'
    }).length
  }


  function handlePageChange(page) {
  loadProposals({ page })
}

function handleSizeChange(size) {
  pagination.page = 1
  loadProposals({ page: 1, size })
}

  async function fetchVotingPower() {
    try {
      if (!walletStore.address) return

      const exthAddress = import.meta.env.VITE_EXTH_CONTRACT_ADDRESS
      if (!exthAddress) return

      const provider = new ethers.BrowserProvider(window.ethereum)
      const exthABI = [
        'function getVotes(address account) external view returns (uint256)'
      ]

      const contract = new ethers.Contract(exthAddress, exthABI, provider)
      const votes = await contract.getVotes(walletStore.address)

      votingPowerLocal.value = Number(ethers.formatEther(votes))
    } catch (error) {
      console.error('Failed to fetch voting power:', error)
    }
  }

  async function fetchDaoStats() {
    try {
      // 从链上获取 DAO 统计信息
      const daoAddress = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
      if (!daoAddress) return

      const provider = new ethers.BrowserProvider(window.ethereum)
      const daoABI = [
        'function proposalCount() external view returns (uint256)',
        'function votingPeriod() external view returns (uint256)'
      ]

      const contract = new ethers.Contract(daoAddress, daoABI, provider)

      const proposalCount = await contract.proposalCount()
      const votingPeriod = await contract.votingPeriod()

      // 简单统计（可以进一步优化）
      daoStatsLocal.value = {
        totalProposals: Number(proposalCount),
        activeProposals: 0, // 需要遍历提案计算
        succeededProposals: 0
      }

      // 更新全局 store
      if (daoStats) {
        daoStats.totalProposals = Number(proposalCount)
      }
    } catch (error) {
      console.error('Failed to fetch dao stats:', error)
    }
  }

// 添加 onMounted 钩子，页面加载时自动获取提案列表
  onMounted(async () => {
    console.log('🚀 ProposalList mounted, loading proposals...')
    try {
      await loadProposals()
      console.log('✅ Proposals loaded successfully')

      // ✅ 加载 DAO 统计信息
      await fetchDaoStats()

      // ✅ 加载投票权
      if (walletStore.isConnected) {
        await fetchVotingPower()
      }
    } catch (error) {
      console.error('❌ Failed to load proposals:', error)
      ElMessage.error('加载提案列表失败')
    }
  })

</script>

<style lang="scss" scoped>
.proposal-list-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .page-title {
        font-size: 18px;
        font-weight: bold;
      }
    }

    .create-proposal-btn {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
      border: none !important;
      color: #fff !important;
      font-weight: 600;
      box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);

      &:hover {
        background: linear-gradient(135deg, #764ba2 0%, #667eea 100%) !important;
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
      }
    }
  }

  .stats-row {
    margin-bottom: 20px;

    .stat-item {
      padding: 20px;
      background: #f5f7fa;
      border-radius: 8px;
      text-align: center;

      .stat-label {
        font-size: 14px;
        color: #606266;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #303133;

        &.success {
          color: #67C23A;
        }

        &.primary {
          color: #409EFF;
        }

        &.warning {
          color: #E6A23C;
        }
      }
    }
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  :deep(.el-table__row) {
    cursor: pointer;

    &:hover {
      background-color: #f5f7fa;
    }
  }
}

.text-danger {
  color: #F56C6C;
}
</style>
