<template>
  <div class="arbitration-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="$router.back()" icon="ArrowLeft" circle size="small" />
            <span class="page-title">仲裁委员会</span>
          </div>
          <div class="header-right">
            <el-tag v-if="isCommitteeMember" type="success">委员会成员</el-tag>
            <el-tag v-else type="info">非委员会成员</el-tag>
            <el-button
                size="small"
                type="primary"
                plain
                @click="testCommitteeStatus"
            >
              测试身份
            </el-button>
          </div>
        </div>
      </template>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-container">
        <el-alert :title="error" type="error" show-icon :closable="false" />
        <div class="error-actions">
          <el-button type="primary" @click="loadData">重试</el-button>
        </div>
      </div>

      <template v-else>
        <!-- 委员会成员信息 -->
        <el-card shadow="never" class="mb-4">
          <template #header>
            <span class="section-title">委员会成员</span>
          </template>
          <el-descriptions :column="3" border>
            <el-descriptions-item
                v-for="(member, index) in committeeMembers"
                :key="member"
                :label="`成员 ${index + 1}`"
            >
              <div class="member-info">
                <el-icon><User /></el-icon>
                <span>{{ formatAddress(member) }}</span>
                <el-tag v-if="member.toLowerCase() === walletStore.address?.toLowerCase()" type="success" size="small">我</el-tag>
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 仲裁规则说明 -->
        <el-alert
            title="仲裁规则"
            type="info"
            :closable="false"
            class="mb-4"
        >
          <template #default>
            <ul class="rule-list">
              <li>委员会由 3 名成员组成，需要至少 2 票同意才能通过裁决</li>
              <li>投票周期为 7 天，超时未通过则提案自动失效</li>
              <li>裁决通过后，系统会自动执行：拉黑说谎方 + 从 Treasure 金库赔偿受害方</li>
              <li>委员会成员会定期轮换，由 DAO 投票决定</li>
            </ul>
          </template>
        </el-alert>

        <!-- 待处理争议（仅委员会成员可见） -->
        <el-card v-if="isCommitteeMember" shadow="never" class="mb-4">
          <template #header>
            <div class="section-header">
              <span class="section-title">待处理争议</span>
              <el-button size="small" @click="loadPendingDisputes" icon="Refresh">刷新</el-button>
            </div>
          </template>

          <el-table :data="pendingDisputes" style="width: 100%">
            <el-table-column prop="tradeId" label="交易 ID" width="120" />
            <el-table-column prop="initiator" label="发起方" width="200">
              <template #default="{ row }">
                {{ row.initiator }}
              </template>
            </el-table-column>
            <el-table-column prop="accused" label="被指控方" width="200">
              <template #default="{ row }">
                {{ row.accused }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="争议原因" min-width="200">
              <template #default="{ row }">
                {{ formatDisputeReason(row.reason) }}
              </template>
            </el-table-column>
            <el-table-column prop="evidence" label="证据" min-width="250">
              <template #default="{ row }">
                {{ row.evidence || '无' }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                    type="primary"
                    size="small"
                    :loading="creatingProposal && creatingProposalTradeId === row.tradeId"
                    @click="handleCreateProposal(row)"
                >
                  创建提案
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="pendingDisputes.length === 0" description="暂无待处理的争议" />
        </el-card>

        <!-- 待处理提案（仅委员会成员可见） -->
        <el-card v-if="isCommitteeMember" shadow="never" class="mb-4">
          <template #header>
            <div class="section-header">
              <span class="section-title">待处理提案</span>
              <el-button size="small" @click="loadData" icon="Refresh">刷新</el-button>
            </div>
          </template>

          <el-table :data="pendingProposals" style="width: 100%">
            <el-table-column prop="proposalId" label="提案 ID" width="80">
              <template #default="{ row }">
                {{ row.proposalId || row.id }}
              </template>
            </el-table-column>
            <el-table-column prop="tradeId" label="交易 ID" width="120" />
            <el-table-column prop="accusedParty" label="被指控方" width="200">
              <template #default="{ row }">
                {{ row.accusedParty }}
              </template>
            </el-table-column>
            <el-table-column prop="victimParty" label="受害方" width="200">
              <template #default="{ row }">
                {{ row.victimParty }}
              </template>
            </el-table-column>
            <el-table-column prop="compensationAmount" label="赔偿金额 (USDT)" width="120">
              <template #default="{ row }">
                {{ formatAmount(row.compensationAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="voteCount" label="赞成票" width="80">
              <template #default="{ row }">
                <el-tag :type="row.voteCount >= 2 ? 'success' : 'warning'">
                  {{ row.voteCount }}/2
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="evidence" label="仲裁原因" min-width="200">
              <template #default="{ row }">
                {{ row.evidence || '无' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="scope">
                <el-button
                    type="success"
                    size="small"
                    :loading="votingId === scope.row.proposalId && votingSupport"
                    @click="handleVote(scope.row.proposalId, true)"
                >
                  支持
                </el-button>
                <el-button
                    type="danger"
                    size="small"
                    :loading="votingId === scope.row.proposalId && !votingSupport"
                    @click="handleVote(scope.row.proposalId, false)"
                >
                  反对
                </el-button>
                <el-button
                    type="primary"
                    size="small"
                    @click="viewProposalDetail(scope.row)"
                >
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="pendingProposals.length === 0" description="暂无待处理的仲裁提案" />
        </el-card>

        <!-- 仲裁历史 -->
        <el-card shadow="never">
          <template #header>
            <div class="section-header">
              <span class="section-title">仲裁历史</span>
              <el-button size="small" @click="loadHistory" icon="Refresh">刷新</el-button>
            </div>
          </template>

          <el-table :data="historyProposals" style="width: 100%">
            <el-table-column prop="tradeId" label="交易 ID" width="120" />
            <el-table-column prop="accusedParty" label="被指控方" width="140">
              <template #default="{ row }">
                {{ formatAddress(row.accusedParty) }}
              </template>
            </el-table-column>
            <el-table-column prop="victimParty" label="受害方" width="140">
              <template #default="{ row }">
                {{ formatAddress(row.victimParty) }}
              </template>
            </el-table-column>
            <el-table-column prop="compensationAmount" label="赔偿金额" width="120">
              <template #default="{ row }">
                {{ formatAmount(row.compensationAmount) }}
              </template>
            </el-table-column>
            <el-table-column prop="result" label="结果" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.executed" type="success">已执行</el-tag>
                <el-tag v-else-if="row.rejected" type="danger">已驳回</el-tag>
                <el-tag v-else type="info">未知</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">
                {{ formatTimestamp(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button
                    type="primary"
                    size="small"
                    @click="viewProposalDetail(row.proposalId)"
                >
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="historyProposals.length === 0" description="暂无仲裁历史" />
        </el-card>
      </template>
    </el-card>

    <!-- 投票确认对话框 -->
    <el-dialog
        v-model="voteDialogVisible"
        title="投票确认"
        width="500px"
    >
      <el-alert
          :title="votingSupport ? '支持该提案' : '反对该提案'"
          :type="votingSupport ? 'success' : 'warning'"
          :closable="false"
      >
        <p>您确定要{{ votingSupport ? '支持' : '反对' }}这个仲裁提案吗？</p>
        <p v-if="votingSupport" class="mt-2 text-sm text-gray-600">
          如果达到 2 票支持，系统将自动执行：拉黑被指控方 + 赔偿受害方
        </p>
      </el-alert>

      <template #footer>
        <el-button @click="voteDialogVisible = false">取消</el-button>
        <el-button
            :type="votingSupport ? 'success' : 'danger'"
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { useWalletStore } from '@/stores'
import { getCommitteeMembers, getPendingProposals, getHistoryProposals, voteProposal, createProposal, getPendingDisputes } from '@/api/arbitration'
import request from '@/utils/request'
import { formatAddress, formatAmount, formatTimestamp } from '@/utils/format'
// 工具函数
function formatDisputeReason(reason) {
  if (!reason) return '无'

  // 将英文原因映射为中文
  const reasonMap = {
    'party_a_not_confirm': '甲方未确认交易',
    'party_b_not_confirm': '乙方未确认交易',
    'payment_not_received': '未收到付款',
    'wrong_amount': '金额错误',
    'fraud_suspected': '涉嫌欺诈',
    'transaction_dispute': '交易争议',
    'other': '其他'
  }

  // 如果 reason 在映射表中，返回中文
  if (reasonMap[reason]) {
    return reasonMap[reason]
  }

  // 否则直接返回原文（可能是自定义描述）
  return reason
}

function formatReason(reason) {
  // 仲裁原因直接使用描述内容，不做映射
  return reason || '无'
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  if (isNaN(date.getTime())) return 'Invalid Date'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}


const router = useRouter()
const walletStore = useWalletStore()


// 状态管理
const loading = ref(false)
const error = ref('')
const voting = ref(false)
const votingId = ref(null)
const votingSupport = ref(true)
const voteDialogVisible = ref(false)
const currentProposalId = ref(null)

// 数据
const committeeMembers = ref([])
const pendingProposals = ref([])
const historyProposals = ref([])
const pendingDisputes = ref([])
const creatingProposal = ref(false)
const creatingProposalTradeId = ref(null)

// 计算当前用户是否为委员会成员
const isCommitteeMember = computed(() => {
  if (!walletStore.address || committeeMembers.value.length === 0) {
    console.log('❌ Not committee member:', {
      hasWalletAddress: !!walletStore.address,
      walletAddress: walletStore.address,
      membersCount: committeeMembers.value.length,
      members: committeeMembers.value
    })
    return false
  }

  const result = committeeMembers.value.some(
      member => member.toLowerCase() === walletStore.address.toLowerCase()
  )

  console.log('🔍 Committee check:', {
    walletAddress: walletStore.address,
    members: committeeMembers.value,
    isMember: result
  })

  return result
})

// 加载数据
async function loadData() {
  loading.value = true
  error.value = ''

  try {
    // 加载委员会成员
    console.log('📡 开始请求委员会成员列表...')
    const membersRes = await getCommitteeMembers()
    console.log('📨 委员会成员完整响应:', membersRes)
    console.log('📨 委员会成员 data:', membersRes.data)
    console.log('📨 委员会成员 data 类型:', Array.isArray(membersRes.data) ? 'Array' : typeof membersRes.data)

    // 兼容两种情况：直接返回数组 或 返回对象包含 data 字段
    if (Array.isArray(membersRes)) {
      committeeMembers.value = membersRes
    } else if (membersRes && Array.isArray(membersRes.data)) {
      committeeMembers.value = membersRes.data
    } else if (membersRes && membersRes.code === 200 && Array.isArray(membersRes.data)) {
      committeeMembers.value = membersRes.data
    } else {
      committeeMembers.value = []
      console.warn('⚠️ 未识别的响应格式:', membersRes)
    }

    console.log('✅ 最终设置的委员会成员:', committeeMembers.value)
    console.log('💼 当前钱包地址:', walletStore.address)

    // 手动检查是否为委员会成员并加载数据
    const isMember = committeeMembers.value.some(
        member => member.toLowerCase() === walletStore.address?.toLowerCase()
    )

    console.log('🔍 手动检查结果:', { isMember, walletAddress: walletStore.address })

    // 加载待处理提案和争议
    if (isMember) {
      console.log('✅ 用户是委员会成员，开始加载待处理提案和争议...')
      // 使用 loadPendingProposals 函数而不是直接赋值
      await loadPendingProposals()

      // 同时加载待处理争议
      await loadPendingDisputes()
    } else {
      console.log('⚠️ 用户不是委员会成员')
    }

    // 加载仲裁历史
    await loadHistory()


  } catch (err) {
    console.error('❌ Failed to load arbitration data:', err)
    console.error('错误详情:', err.message)
    console.error('完整错误:', err)
    error.value = err.message || '加载失败，请重试'
  } finally {
    loading.value = false
  }
}



// 加载仲裁历史
async function loadHistory() {
  try {
    const historyRes = await getHistoryProposals()
    historyProposals.value = historyRes.data || []
  } catch (err) {
    console.error('Failed to load history:', err)
  }
}

// 加载待处理争议
const loadPendingDisputes = async () => {
  try {
    console.log('📋 请求待处理争议列表...')
    const res = await getPendingDisputes()
    console.log('待处理争议响应:', res)

    // ✅ 兼容两种返回格式：res.data 或 res
    let disputeList = []
    if (Array.isArray(res)) {
      disputeList = res
    } else if (res && Array.isArray(res.data)) {
      disputeList = res.data
    } else {
      console.warn('⚠️ 待处理争议数据格式异常:', res)
      disputeList = []
    }

    pendingDisputes.value = disputeList
    console.log('✅ 待处理争议数量:', pendingDisputes.value.length)
  } catch (error) {
    console.error('Failed to load pending disputes:', error)
    pendingDisputes.value = []
  }
}


// 加载待处理提案
async function loadPendingProposals() {
  try {
    console.log('📋 请求待处理提案列表...')
    const res = await request({
      url: '/arbitration/proposal/pending',
      method: 'get'
    })

    console.log('✅ 待处理提案响应:', res)
    console.log('📊 待处理提案 data:', res.data)
    console.log('🔍 第一个提案的完整数据:', res.data[0])

    if (res.data && res.data.length > 0) {
      pendingProposals.value = res.data
      console.log('⏰ 第一个提案的 deadline:', res.data[0]?.deadline)
      console.log('🆔 第一个提案的 proposalId:', res.data[0]?.proposalId)
      console.log('🔢 第一个提案的 id:', res.data[0]?.id)
    } else {
      pendingProposals.value = []
    }
    console.log(`📝 待处理提案数量：${pendingProposals.value.length}`)
  } catch (error) {
    console.error('❌ 获取待处理提案失败:', error)
    ElMessage.error('获取待处理提案失败')
  }
}



// 创建仲裁提案
async function handleCreateProposal(dispute) {
  try {
    // 让用户输入赔偿金额
    const { value: compensationAmount } = await ElMessageBox.prompt(
        '请输入赔偿金额（USDT）：',
        '设置赔偿金额',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPattern: /^[0-9]+(\.[0-9]{1,18})?$/,
          inputErrorMessage: '请输入有效的数字',
          inputValue: '100' // 默认值
        }
    )

    creatingProposal.value = true
    creatingProposalTradeId.value = dispute.tradeId

    console.log('📤 创建仲裁提案（不自动投票）:', {
      tradeId: dispute.tradeId,
      accusedParty: dispute.accused,
      victimParty: dispute.initiator,
      compensationAmount,
      reason: dispute.reason,
      description: dispute.description
    })

    console.log('🔗 调用后端 API 创建提案...')
    const txHash = await createProposal({
      tradeId: dispute.tradeId,
      accusedParty: dispute.accused,
      victimParty: dispute.initiator,
      compensationAmount,
      reason: dispute.reason,
      description: dispute.description
    })

    console.log('✅ 提案创建成功！交易哈希:', txHash)

    ElMessage.success('提案创建成功！交易哈希：' + txHash)

    // 等待区块确认
    console.log('⏳ 等待区块确认...')
    await new Promise(resolve => setTimeout(resolve, 5000)) // 等待 5 秒

    // 直接通过后端 API 刷新列表，不依赖链上查询
    await loadPendingDisputes()
    await loadPendingProposals()

  } catch (error) {
    if (error !== 'cancel') {
      console.error('❌ 创建提案失败:', error)
      console.error('错误堆栈:', error.stack)
      ElMessage.error('创建提案失败：' + (error.message || '未知错误'))
    }
  } finally {
    creatingProposal.value = false
    creatingProposalTradeId.value = null
  }
}

// 处理投票
function handleVote(proposalId, support) {
  currentProposalId.value = proposalId
  votingSupport.value = support
  voteDialogVisible.value = true
}

// 确认投票
async function confirmVote() {
  voting.value = true

  try {
    // 使用前端钱包签名投票
    const hash = await voteProposal(currentProposalId.value, votingSupport.value)
    console.log('✅ 投票成功，交易哈希:', hash)
    ElMessage.success('投票成功')
    voteDialogVisible.value = false
    await loadData()
  } catch (err) {
    console.error('❌ Vote error:', err)

    // 解析错误信息
    let errorMessage = err.message || '投票失败'

    if (err.reason) {
      errorMessage = err.reason
    } else if (err.data && err.data.message) {
      errorMessage = err.data.message
    } else if (err.message && err.message.includes('execution reverted')) {
      // 尝试提取 revert reason
      const match = err.message.match(/reverted with reason string '([^']+)'/)
      if (match) {
        errorMessage = match[1]
      } else {
        errorMessage = '交易执行失败，可能原因：\n1. 投票期已结束\n2. 已经投过票了\n3. 提案已解决'
      }
    }

    ElMessage.error(errorMessage)
  } finally {
    voting.value = false
  }
}

// 查看提案详情
// 查看提案详情
function viewProposalDetail(row) {
  console.log('📋 查看提案详情，row:', row)

  // 确保传递正确的 proposalId
  if (!row) {
    console.error('❌ row 为 null 或 undefined')
    ElMessage.error('提案数据无效')
    return
  }

  // 使用 != null 检查，这样可以同时检测 null 和 undefined，但允许 0
  const proposalId = row.proposalId != null ? row.proposalId : (row.id != null ? row.id : null)

  if (proposalId === null) {
    console.error('❌ proposalId 不存在:', row)
    ElMessage.error('提案 ID 不存在')
    return
  }

  console.log('✅ 跳转提案详情页，proposalId:', proposalId)
  router.push(`/arbitration/proposal/${proposalId}`)
}

// 初始化
onMounted(() => {
  loadData()
})

// 测试委员会身份
async function testCommitteeStatus() {
  try {
    const res = await request({
      url: '/arbitration/check-committee',
      method: 'get'
    })

    ElMessage.info(`后端检查结果：${res.data ? '是委员会成员' : '不是委员会成员'}`)

    console.log('🧪 Test result:', {
      frontend: isCommitteeMember.value,
      backend: res.data,
      walletAddress: walletStore.address,
      membersFromBackend: committeeMembers.value
    })
  } catch (error) {
    ElMessage.error('测试失败：' + error.message)
  }
}
</script>

<style scoped lang="scss">
.arbitration-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

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
      font-weight: 600;
    }
  }

  .header-right {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.section-title {
  font-size: 16px;
  font-weight: 600;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.member-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rule-list {
  margin: 0;
  padding-left: 20px;

  li {
    margin: 8px 0;
    line-height: 1.6;
  }
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
