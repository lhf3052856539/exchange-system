<template>
  <div class="trade-detail">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>交易详情</span>
          <el-button @click="handleBack" icon="ArrowLeft">返回</el-button>
        </div>
      </template>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-container">
        <el-alert
            :title="error"
            type="error"
            show-icon
            :closable="false"
        />
        <div class="error-actions">
          <el-button type="primary" @click="loadTradeDetail">重试</el-button>
        </div>
      </div>



      <!-- 正常内容 -->
      <template v-else-if="trade">
        <!-- 交易基本信息 -->
        <el-descriptions :column="2" border size="large" class="mb-4">
          <el-descriptions-item label="交易 ID">{{ trade.tradeId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTag(getStatus)">
              {{ getStatusText(getStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="交易对">
            {{ trade.fromCurrency }}/{{ trade.toCurrency }}
          </el-descriptions-item>
          <el-descriptions-item label="金额 (UT)">
            {{ trade.amount }}
          </el-descriptions-item>
          <el-descriptions-item label="匹配汇率">            1 {{ trade.fromCurrency }} = {{ trade.exchangeRate }} {{ trade.toCurrency }}
          </el-descriptions-item>
          <el-descriptions-item label="我的角色">
            <el-tag v-if="isPartyA" type="success">甲方</el-tag>
            <el-tag v-else type="warning">乙方</el-tag>
          </el-descriptions-item>
          <!-- 添加授权信息显示 -->
          <el-descriptions-item
              v-if="getStatus === TRADE_STATUS.MATCHED || getStatus === TRADE_STATUS.CONFIRMING_A || getStatus === TRADE_STATUS.PARTY_A_CONFIRMED || getStatus === TRADE_STATUS.CONFIRMING_B || getStatus === TRADE_STATUS.PARTY_B_CONFIRMED"
              label="授权状态"
          >
            <el-tag v-if="needApprove" type="warning">未授权</el-tag>
            <el-tag v-else type="success">已授权</el-tag>
            <span v-if="!needApprove" style="margin-left: 8px; font-size: 12px; color: #999;">
              (额度：{{ allowance }})
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 交易双方信息 -->
        <el-card shadow="never" class="mt-4">
          <template #header>
            <div class="step-header">
              <span>交易双方</span>
            </div>
          </template>

          <div class="parties-container">
            <div class="party-card">
              <h3>甲方 (Party A)</h3>
              <p><strong>地址:</strong> <span style="font-family: 'Courier New', monospace; font-size: 13px; word-break: break-all;">{{ trade.partyA }}</span></p>
              <p><strong>应发送金额:</strong> {{ formatAmount(trade.amountA) }} {{ trade.fromCurrency }}</p>
              <p><strong>转账哈希:</strong> {{ trade.txHashA || '待确认' }}</p>
              <p><strong>状态:</strong>
                <el-tag v-if="trade.txHashA" type="success">已转账</el-tag>
                <el-tag v-else type="warning">待转账</el-tag>
              </p>
            </div>

            <div class="party-card">
              <h3>乙方 (Party B)</h3>
              <p><strong>地址:</strong> <span style="font-family: 'Courier New', monospace; font-size: 13px; word-break: break-all;">{{ trade.partyB }}</span></p>
              <p><strong>应发送金额:</strong> {{ formatAmount(trade.amountB) }} {{ trade.toCurrency }}</p>
              <p><strong>收款哈希:</strong> {{ trade.txHashB || '待确认' }}</p>
              <p><strong>状态:</strong>
                <el-tag v-if="trade.txHashB" type="success">已收款</el-tag>
                <el-tag v-else type="warning">待收款</el-tag>
              </p>
            </div>
          </div>
        </el-card>

        <!-- 操作按钮 -->
        <div v-if="showActionButtons" class="action-buttons mt-4">
          <!-- 添加调试信息来检查条件 -->
          <div v-if="trade && trade.status" style="color: #999; font-size: 12px; margin-bottom: 8px;">
          </div>

          <el-button
              v-if="canStartConfirmProcess"
              type="primary"
              size="large"
              @click="startConfirmProcess">
            开始转账确认
          </el-button>

          <!-- 添加操作指南 -->
          <div v-if="canStartConfirmProcess" class="transfer-guide mt-4">
            <el-alert type="info" :closable="false" title="操作指引">
              <div><strong>请按以下步骤操作：</strong></div>
              <ol>
                <li>打开您的钱包（如 MetaMask）</li>
                <li>向以下地址转账</li>
                <li>确保金额和代币类型正确</li>
                <li>等待交易被网络确认</li>
                <li>复制交易哈希并在此提交</li>
              </ol>
              <div style="margin-top: 10px;">
                <p><strong>收款地址：</strong><code style="background:#f0f0f0;padding:2px 6px;border-radius:3px;font-family:monospace">{{ trade.partyB }}</code></p>
                <p><strong>转账金额：</strong>{{ formatAmount(trade.amountA) }} {{ trade.fromCurrency }}</p>
                <p><strong>对方角色：</strong>乙方 (Party B)</p>
              </div>
            </el-alert>
          </div>

          <el-button
              v-if="canConfirmTransfer"
              type="primary"
              size="large"
              @click="handleConfirmTransfer"
          >
            {{ isPartyA ? '确认转账' : '确认转账' }}
          </el-button>

          <el-button
              v-if="canConfirmReceipt"
              type="success"
              size="large"
              @click="handleConfirmReceipt"
          >
            {{ isPartyA ? '确认收款' : '确认收款' }}
          </el-button>

          <!-- 添加授权按钮 -->
          <el-button
              v-if="needApprove"
              type="warning"
              size="large"
              :loading="approving"
              @click="handleApprove"
          >
            授权 EXTH（手续费）
          </el-button>

          <!-- 最终确认按钮 -->
          <el-button
              v-if="canFinalConfirm"
              type="success"
              size="large"
              :loading="processing"
              @click="handleFinalConfirm"
          >
            确认完成交易
          </el-button>

          <el-button
              v-if="canDispute"
              type="danger"
              size="large"
              @click="handleDispute"
          >
            发起争议
          </el-button>

          <el-button
              v-if="getStatus === TRADE_STATUS.DISPUTED"
              type="warning"
              size="large"
              @click="showArbitrationProgress"
          >
            查看仲裁进度
          </el-button>
        </div>

      </template>

      <!-- 无数据状态 -->
      <div v-else class="no-data-container">
        <el-empty description="未找到交易详情" />
      </div>
    </el-card>

    <!-- 确认对话框 -->
    <el-dialog
        v-model="confirmDialogVisible"
        :title="isConfirmingTransfer ? '确认转账' : '确认收款'"
        width="500px"
    >
      <el-form :model="confirmForm" label-width="100px">
        <el-form-item label="交易哈希">
          <el-input
              v-model="confirmForm.txHash"
              placeholder="请输入交易哈希"
              :input-style="{ fontFamily: 'monospace' }"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            :loading="confirming"
            @click="submitConfirm"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 争议对话框 -->
    <el-dialog
        v-model="disputeDialogVisible"
        title="发起争议"
        width="500px"
    >
      <el-form :model="disputeForm" label-width="100px">
        <el-form-item label="争议原因">
          <el-select v-model="disputeForm.reason" placeholder="请选择原因" style="width: 100%">
            <el-option label="对方未按时转账" value="NOT_TRANSFERRED" />
            <el-option label="转账金额不符" value="WRONG_AMOUNT" />
            <el-option label="其他原因" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
              v-model="disputeForm.description"
              type="textarea"
              :rows="4"
              placeholder="请详细描述问题"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disputeDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            :loading="submittingDispute"
            @click="submitDisputeHandler"
        >
          提交
        </el-button>
      </template>
    </el-dialog>
    <!-- 在争议对话框后添加仲裁状态展示 -->
    <el-dialog
        v-model="arbitrationDialogVisible"
        title="仲裁进度"
        width="600px"
    >
      <el-steps :active="arbitrationStep" finish-status="success" align-center>
        <el-step title="争议提交" />
        <el-step title="委员会审查" />
        <el-step title="投票表决" />
        <el-step title="执行裁决" />
      </el-steps>

      <el-card shadow="never" class="mt-4">
        <h4>仲裁信息</h4>
        <p><strong>提案 ID:</strong> {{ arbitrationInfo.proposalId }}</p>
        <p><strong>赞成票:</strong> {{ arbitrationInfo.yesVotes }} / 2</p>
        <p><strong>反对票:</strong> {{ arbitrationInfo.noVotes }}</p>
        <p><strong>截止时间:</strong> {{ formatTimestamp(arbitrationInfo.deadline) }}</p>
        <p><strong>状态:</strong>
          <el-tag :type="ARBITRATION_STATUS_TAG[arbitrationInfo.status] || 'info'">
            {{ ARBITRATION_STATUS_TEXT[arbitrationInfo.status] || arbitrationInfo.status || '未知状态' }}
          </el-tag>
        </p>
      </el-card>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTradeStore } from '@/stores'
import { useWalletStore } from '@/stores'
import { useTrade } from '@/composables/useTrade'
import { TRADE_STATUS, TRADE_STATUS_CODE } from '@/config/constants'
import * as ethers from 'ethers'

// 仲裁状态映射
const ARBITRATION_STATUS_TEXT = {
  0: '无争议',
  1: '争议处理中',
  2: '争议请求已执行',
  3: '争议请求已驳回',
  4: '争议已过期'
}

const ARBITRATION_STATUS_TAG = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
  4: 'info'
}



const router = useRouter()
const route = useRoute()
const tradeStore = useTradeStore()
const walletStore = useWalletStore()
const { loadDetail, confirmTrade, submitDispute, getStatusTag, getStatusText } = useTrade()

// 状态管理
const loading = ref(false)
const error = ref('')
const debugInfo = ref('')
const approving = ref(false)
const processing = ref(false)
const allowance = ref('0')
const needApprove = ref(false)
const approvalConfirmed = ref(false)

// 使用 computed 确保响应式更新
const trade = computed(() => {
  const current = tradeStore.currentTrade
  console.log('🔄 Computed trade triggered:', current)
  return current
})
// 获取正确的状态值
const getStatus = computed(() => {
  if (!trade.value) return null

  if (typeof trade.value.status === 'number') {
    const statusMap = {
      0: TRADE_STATUS.MATCHED,
      1: TRADE_STATUS.PARTY_A_CONFIRMED,
      2: TRADE_STATUS.PARTY_B_CONFIRMED,
      3: TRADE_STATUS.COMPLETED,
      4: TRADE_STATUS.CANCELLED,
      5: TRADE_STATUS.DISPUTED,
      6: TRADE_STATUS.FAILED,
      7: TRADE_STATUS.EXPIRED,
      9: TRADE_STATUS.PENDING_CHAIN_CONFIRM
    }
    return statusMap[trade.value.status] || trade.value.status
  }

  return trade.value.status
})
// 计算当前用户是否为甲方
const isPartyA = computed(() => {
  return trade.value?.partyA === walletStore.address
})

// 确认相关
const confirmDialogVisible = ref(false)
const isConfirmingTransfer = ref(true)
const confirming = ref(false)
const confirmForm = ref({
  txHash: ''
})

// 争议相关
const disputeDialogVisible = ref(false)
const submittingDispute = ref(false)
const disputeForm = ref({
  reason: '',
  description: ''
})

// 仲裁相关
const arbitrationDialogVisible = ref(false)
const arbitrationStep = ref(0)
const arbitrationInfo = ref({
  proposalId: null,
  yesVotes: 0,
  noVotes: 0,
  deadline: null,
  status: '',
  executed: false,
  rejected: false
})


// 计算属性
const partyAConfirmed = computed(() => {
  return !!trade.value?.partyATxHash
})

const partyBConfirmed = computed(() => {
  return !!trade.value?.txHashB
})


const canStartConfirmProcess = computed(() => {
  return getStatus.value === TRADE_STATUS.MATCHED &&
      isPartyA.value
})

const canConfirmTransfer = computed(() => {
  // 甲方在 CONFIRMING_A 状态下确认转账
  if (getStatus.value === TRADE_STATUS.CONFIRMING_A && isPartyA.value && !trade.value.partyATxHash) {
    return true
  }
  // 乙方在 PARTY_A_CONFIRMED 状态下确认转账（甲方已确认，等待乙方）
  if (getStatus.value === TRADE_STATUS.PARTY_A_CONFIRMED && !isPartyA.value && !trade.value.partyBTxHash) {
    return true
  }
  // 乙方在 CONFIRMING_B 状态下确认转账
  if (getStatus.value === TRADE_STATUS.CONFIRMING_B && !isPartyA.value && !trade.value.partyBTxHash) {
    return true
  }
  return false
})


const canConfirmReceipt = computed(() => {
  // 等待甲方转账
  if (getStatus.value === TRADE_STATUS.MATCHED && !isPartyA.value) {
    return true
  }
  // 等待乙方转账
  if (getStatus.value === TRADE_STATUS.PARTY_A_CONFIRMED && !isPartyA.value) {
    return true
  }
  // 等待甲方最终确认
  if (getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED && isPartyA.value) {
    return true
  }

  return false
})

const canFinalConfirm = computed(() => {
  // 甲方在 PARTY_B_CONFIRMED 状态下进行最终确认
  const result = getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED && isPartyA.value

  console.log('🔵 Can final confirm:', {
    status: getStatus.value,
    isPartyBConfirmed: getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED,
    isPartyA: isPartyA.value,
    result
  })

  return result
})


const showActionButtons = computed(() => {
  return canStartConfirmProcess.value ||
      canConfirmTransfer.value ||
      canConfirmReceipt.value ||
      canFinalConfirm.value ||
      canDispute.value
})

const canDispute = computed(() => {
  return getStatus.value !== TRADE_STATUS.COMPLETED &&
      getStatus.value !== TRADE_STATUS.FAILED &&
      getStatus.value !== TRADE_STATUS.DISPUTED &&
      trade.value?.partyA &&
      trade.value?.partyB
})


// 添加调试日志
console.log('=== Trade Detail Debug Info ===')
console.log('Current user address:', walletStore.address)
console.log('Trade partyA:', trade.value?.partyA)
console.log('Trade partyB:', trade.value?.partyB)
console.log('Is Party A:', isPartyA.value)
console.log('Trade status (raw):', trade.value?.status)
console.log('Trade status (mapped):', getStatus.value)
console.log('Can confirm transfer:', canConfirmTransfer.value)
console.log('Can confirm receipt:', canConfirmReceipt.value)
console.log('Show action buttons:', showActionButtons.value)
console.log('===========================')


// 方法
async function handleBack() {
  router.back()
}

// 显示仲裁进度
async function showArbitrationProgress() {
  if (!trade.value?.tradeId) return

  try {
    //调用 API 获取仲裁进度
    const res = await getArbitrationProgress(trade.value.tradeId)
    arbitrationInfo.value = res.data

    // 模拟数据
    arbitrationInfo.value = {
      proposalId: '1',
      yesVotes: 1,
      noVotes: 0,
      deadline: Date.now() / 1000 + 86400 * 7,
      status: 1,
      executed: false,
      rejected: false
    }

    // 设置进度步骤
    if (arbitrationInfo.value.status === 2) {
      arbitrationStep.value = 4
    } else if (arbitrationInfo.value.status === 3) {
      arbitrationStep.value = 2
    } else if (arbitrationInfo.value.status === 1) {
      arbitrationStep.value = 2
    } else if (arbitrationInfo.value.status === 0) {
      arbitrationStep.value = 0
    } else {
      arbitrationStep.value = 3
    }

    arbitrationDialogVisible.value = true

  } catch (err) {
    console.error('Failed to load arbitration progress:', err)
    ElMessage.error('加载仲裁进度失败')
  }
}
// 开始确认流程 - 直接开始转账确认
async function startConfirmProcess() {
  try {
    await handleConfirmTransfer()
  } catch (error) {
    ElMessage.error('启动确认流程失败，请重试')
  }
}

function formatAmount(amount) {
  return Number(amount).toFixed(6)
}

function handleConfirmTransfer() {
  if (needApprove.value) {
    ElMessage.warning('请先授权 EXTH 手续费')
    return
  }
  isConfirmingTransfer.value = true
  confirmForm.value.txHash = ''
  confirmDialogVisible.value = true
}

function handleConfirmReceipt() {
  if (needApprove.value) {
    ElMessage.warning('请先授权 EXTH 手续费')
    return
  }
  isConfirmingTransfer.value = false
  confirmForm.value.txHash = ''
  confirmDialogVisible.value = true
}

async function handleFinalConfirm() {
  try {
    await ElMessageBox.confirm(
        '请确认您已收到乙方的转账，确认后将完成整个交易。',
        '最终确认',
        { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )

    console.log('🔍 Final confirm check:', {
      chainTradeId: trade.value.chainTradeId,
      status: trade.value.status,
      getStatus: getStatus.value,
      isPartyA: isPartyA.value
    })

    if (!trade.value.chainTradeId) {
      ElMessage.error('链上交易 ID 不存在')
      return
    }

    if (trade.value.status !== 2) {
      ElMessage.error(`当前状态不允许完成交易，当前状态: ${trade.value.status}`)
      return
    }

    confirming.value = true
    try {
      const { ethers } = await import('ethers')
      const provider = new ethers.BrowserProvider(window.ethereum)
      const signer = await provider.getSigner()

      const exthABI = [
        "function allowance(address owner, address spender) view returns (uint256)",
        "function balanceOf(address account) view returns (uint256)",
        "function decimals() view returns (uint8)"
      ]

      const exchangeABI = [
        "function completeTrade(uint256 tradeId) external returns (bool)",
        "function treasure() view returns (address)",
        "function tradePairs(uint256) view returns (tuple(address partyA, address partyB, uint256 amount, uint256 exthReward, uint256 feeAmount, uint256 createTime, uint256 completeTime, uint8 state, address disputedParty))"
      ]

      const exchangeAddress = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
      const exthAddress = import.meta.env.VITE_EXTH_CONTRACT_ADDRESS

      const exthContract = new ethers.Contract(exthAddress, exthABI, signer)
      const exchangeContract = new ethers.Contract(exchangeAddress, exchangeABI, signer)

      const chainTradeId = trade.value.chainTradeId

      console.log(' Checking allowances before completeTrade...')

      const partyAAllowance = await exthContract.allowance(trade.value.partyA, exchangeAddress)
      const partyBAllowance = await exthContract.allowance(trade.value.partyB, exchangeAddress)

      console.log(' Allowances:', {
        partyA: trade.value.partyA,
        partyAAllowance: partyAAllowance.toString(),
        partyB: trade.value.partyB,
        partyBAllowance: partyBAllowance.toString()
      })

      // 使用 trade.value.feeAmount 替代硬编码的 50000
      const feeAmountInWei = ethers.parseUnits((trade.value.feeAmount || 0.01).toString(), 6)

      if (partyAAllowance < feeAmountInWei) {
        ElMessage.warning('甲方未授权 EXTH 手续费，请先授权')
        return
      }

      if (partyBAllowance < feeAmountInWei) {
        ElMessage.warning('乙方未授权 EXTH 手续费，请先授权')
        return
      }

      console.log(' Checking treasure contract and rewards...')
      const treasureAddress = await exchangeContract.treasure()
      console.log(' Treasure address:', treasureAddress)

      if (treasureAddress === ethers.ZeroAddress) {
        ElMessage.error('金库合约未设置，请联系管理员')
        return
      }

      const tradeInfo = await exchangeContract.tradePairs(chainTradeId)
      console.log(' Trade info from chain:', {
        partyA: tradeInfo.partyA,
        partyB: tradeInfo.partyB,
        amount: tradeInfo.amount.toString(),
        exthReward: tradeInfo.exthReward.toString(),
        feeAmount: tradeInfo.feeAmount.toString(),
        state: tradeInfo.state
      })

      // 修改：直接从 EXTH 合约查询 Treasure 的余额，而不是调用不存在的 getERC20Balance
      const treasureExthBalance = await exthContract.balanceOf(treasureAddress)
      console.log(' Treasure EXTH balance:', treasureExthBalance.toString())

      const totalRewardNeeded = tradeInfo.exthReward * BigInt(2)
      console.log(' Total reward needed (both parties):', totalRewardNeeded.toString())

      if (treasureExthBalance < totalRewardNeeded) {
        ElMessage.error(`金库 EXTH 余额不足，需要 ${ethers.formatUnits(totalRewardNeeded, 6)} EXTH，当前余额 ${ethers.formatUnits(treasureExthBalance, 6)} EXTH`)
        return
      }

      console.log(' All checks passed, calling completeTrade with chainTradeId:', chainTradeId)

      const tx = await exchangeContract.completeTrade(chainTradeId)

      console.log('Transaction sent:', tx.hash)
      ElMessage.info('等待交易确认中...')

      const receipt = await tx.wait()

      console.log('Complete trade confirmed:', receipt.transactionHash)
      ElMessage.success('交易已完成！')

      await loadTradeDetail()
    } catch (error) {
      console.error('Complete trade failed:', error)

      let errorMsg = '操作失败'

      // 新增：解析 0x3ee5aeb5 错误码
      if (error.data && error.data.data === '0x3ee5aeb5') {
        // 0x3ee5aeb5 对应 "Invalid state: waiting for confirmations or disputed"
        errorMsg = '交易状态不正确，请确认双方都已完成链上确认'

        // 自动刷新链上状态
        try {
          const exchangeContract = new ethers.Contract(
              import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS,
              ["function tradePairs(uint256) view returns (tuple(address, address, uint256, uint256, uint256, uint256, uint256, uint8, address))"],
              ethers.provider
          );
          const chainTradeInfo = await exchangeContract.tradePairs(trade.value.chainTradeId);
          console.log("链上实际状态:", chainTradeInfo.state);
        } catch (e) {
          console.error("Failed to fetch chain state:", e);
        }
      } else if (error.message && error.message.includes('Treasure not set')) {
        errorMsg = '金库合约未设置，请联系管理员'
      } else if (error.reason) {
        errorMsg = error.reason
      } else if (error.message) {
        errorMsg = error.message
      }

      ElMessage.error(errorMsg)
    } finally {
      confirming.value = false
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Final confirm cancelled:', error)
    }
  }
}

function handleDispute() {
  disputeForm.value = {
    reason: '',
    description: ''
  }
  disputeDialogVisible.value = true
}

async function submitConfirm() {
  if (!confirmForm.value.txHash) {
    ElMessage.warning('请输入交易哈希')
    return
  }

  if (!/^(0x)?[0-9a-fA-F]{64}$/.test(confirmForm.value.txHash)) {
    ElMessage.warning('交易哈希格式不正确，应为 64 位十六进制数（可带 0x 前缀）')
    return
  }

  confirming.value = true
  try {
    // 构建 URL 参数
    const params = new URLSearchParams({
      tradeId: trade.value.tradeId,
      txHash: confirmForm.value.txHash
    })

    const confirmUrl = isPartyA.value
        ? '/trade/confirm-party-a'
        : '/trade/confirm-party-b'

    const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8096/apis'}${confirmUrl}?${params}`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token')}`,
            'Content-Type': 'application/json'
          }
        }
    )


    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.message || '后端校验失败')
    }

    console.log('✅ 后端校验通过，txHash已保存')

    // 2. 再调用链上合约
    const { ethers } = await import('ethers')
    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    const exchangeABI = [
      "function confirmPartyA(uint256 tradeId, string calldata txHash) external",
      "function confirmPartyB(uint256 tradeId, string calldata txHash) external"
    ]

    const exchangeAddress = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    const exchangeContract = new ethers.Contract(exchangeAddress, exchangeABI, signer)

    const chainTradeId = trade.value.chainTradeId

    let tx
    if (isPartyA.value) {
      console.log('Calling chain confirmPartyA with chainTradeId:', chainTradeId, 'txHash:', confirmForm.value.txHash)
      tx = await exchangeContract.confirmPartyA(chainTradeId, confirmForm.value.txHash)
    } else {
      console.log('Calling chain confirmPartyB with chainTradeId:', chainTradeId, 'txHash:', confirmForm.value.txHash)
      tx = await exchangeContract.confirmPartyB(chainTradeId, confirmForm.value.txHash)
    }

    console.log('Transaction sent:', tx.hash)
    await tx.wait()

    console.log('Chain confirmation confirmed:', tx.hash)

    confirmDialogVisible.value = false
    if (trade.value.chainTradeId) {
      await loadDetail(trade.value.chainTradeId.toString())
    } else {
      await loadTradeDetail()
    }
    ElMessage.success('确认转账成功，等待链上确认')

  } catch (error) {
    console.error('Confirm transfer failed:', error)
    ElMessage.error('操作失败：' + (error.reason || error.message || '未知错误'))
  } finally {
    confirming.value = false
  }
}


async function submitDisputeHandler() {
  if (!disputeForm.value.reason) {
    ElMessage.warning('请选择争议原因')
    return
  }

  if (!trade.value?.tradeId) {
    ElMessage.error('交易 ID 为空')
    return
  }

  const payload = {
    chainTradeId: trade.value.chainTradeId,
    reason: disputeForm.value.reason,
    evidence: disputeForm.value.description
  }

  submittingDispute.value = true
  try {
    await submitDispute(payload)
    disputeDialogVisible.value = false
    if (trade.value.chainTradeId) {
      await loadDetail(trade.value.chainTradeId.toString())
    } else {
      await loadTradeDetail()
    }
    ElMessage.success('争议提交成功')
  } catch (error) {
    console.error('Dispute failed:', error)
    ElMessage.error('提交失败：' + (error.message || '未知错误'))
  } finally {
    submittingDispute.value = false
  }
}

function formatAddress(address) {
  if (!address) return 'N/A'
  return address.slice(0, 6) + '...' + address.slice(-4)
}

function formatTime(timestamp) {
  if (!timestamp) return 'N/A'
  return new Date(timestamp).toLocaleString('zh-CN')
}

function getBlockExplorerUrl(txHash) {
  // 这里可以根据实际的区块链浏览器URL进行配置
  return `https://etherscan.io/tx/${txHash}`
}

function getDisputeReasonText(reason) {
  const reasons = {
    NOT_TRANSFERRED: '对方未按时转账',
    WRONG_AMOUNT: '转账金额不符',
    OTHER: '其他原因'
  }
  return reasons[reason] || reason
}

// 监听交易详情变化，检查授权状态
watch(() => trade.value, async (newTrade) => {
  if (newTrade && (getStatus.value === TRADE_STATUS.MATCHED ||
      getStatus.value === TRADE_STATUS.PARTY_A_CONFIRMED ||
      getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED)) {
    await checkAllowanceStatus()
  }
}, { immediate: true })

// 检查授权状态（直接读取链上数据）
async function checkAllowanceStatus() {
  try {
    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()
    const address = await signer.getAddress()

    const exthABI = [
      "function allowance(address owner, address spender) view returns (uint256)",
      "function decimals() view returns (uint8)"
    ]

    const exthContract = new ethers.Contract(
        import.meta.env.VITE_EXTH_CONTRACT_ADDRESS,
        exthABI,
        provider
    )

    const allowanceValue = await exthContract.allowance(
        address,
        import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    )

    allowance.value = allowanceValue.toString()

    // 修改：使用 trade.value.feeAmount 替代硬编码的 50000
    const feeAmountInWei = ethers.parseUnits((trade.value.feeAmount || 0.01).toString(), 6)

    needApprove.value = allowanceValue < feeAmountInWei
    approvalConfirmed.value = !needApprove.value

    console.log('Allowance check:', {
      allowance: allowance.value,
      feeAmount: feeAmountInWei.toString(),
      feeAmountHuman: trade.value.feeAmount,
      needApprove: needApprove.value,
      spender: import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    })
  } catch (err) {
    console.error('Failed to check allowance:', err)
  }
}



// 处理授权（前端直接发送交易）
async function handleApprove() {
  try {
    approving.value = true

    if (!window.ethereum) {
      ElMessage.error('请安装 MetaMask')
      return
    }

    const feeAmount = trade.value.feeAmount || 0.01
    const roleText = isPartyA.value ? '甲方' : '乙方'

    await ElMessageBox.confirm(
        `${roleText}需要授权 Exchange 合约使用 ${feeAmount} EXTH 支付本次交易手续费，是否继续？`,
        '授权确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    const exthABI = [
      "function approve(address spender, uint256 amount) returns (bool)",
      "function decimals() view returns (uint8)"
    ]

    const exthContract = new ethers.Contract(
        import.meta.env.VITE_EXTH_CONTRACT_ADDRESS,
        exthABI,
        signer
    )

    const amount = ethers.parseUnits(feeAmount.toString(), 6)

    console.log(' Approving EXTH for this trade:', feeAmount, 'EXTH')

    const tx = await exthContract.approve(
        import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS,
        amount
    )

    console.log('Approve tx sent:', tx.hash)
    ElMessage.info('等待交易确认中...')

    const receipt = await tx.wait()

    console.log('Approve confirmed:', receipt.transactionHash)
    ElMessage.success(`授权成功！本次授权额度：${feeAmount} EXTH`)

    await checkAllowanceStatus()

  } catch (err) {
    if (err !== 'cancel') {
      console.error('Approve failed:', err)
      ElMessage.error('授权失败：' + (err.message || '请稍后重试'))
    }
  } finally {
    approving.value = false
  }
}


async function loadTradeDetail() {
  loading.value = true
  error.value = ''
  debugInfo.value = ''

  try {
    console.log('⏳ Before loadDetail - trade:', trade.value)

    const tradeId = route.params.id

    if (trade.value && trade.value.chainTradeId) {
      console.log('🔄 Using chainTradeId from existing trade:', trade.value.chainTradeId)
      await loadDetail(trade.value.chainTradeId.toString())
    } else {
      console.log('🔄 Using route param as tradeId:', tradeId)
      await loadDetail(tradeId)
    }


    // 等待下一个 tick，确保响应式更新完成
    await new Promise(resolve => setTimeout(resolve, 100))

    console.log('📦 Raw trade data from API:', tradeStore.currentTrade)
    console.log('📦 Trade status from API:', tradeStore.currentTrade?.status)
    console.log('📦 After loadDetail - trade:', trade.value)

    if (trade.value) {
      console.log('✅ Trade detail loaded successfully')
      console.log('   - Status:', trade.value.status)
      console.log('   - Party A:', trade.value.partyA)
      console.log('   - Party B:', trade.value.partyB)

      // 加载授权状态
      if (isPartyA.value && getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED) {
        await checkAllowanceStatus()
      }
    }
  } catch (err) {
    error.value = err.message || '加载交易详情失败，请重试'
    console.error('Load detail failed:', err)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (walletStore.address) {
    await loadTradeDetail()
  } else {
    error.value = '钱包未连接，请先连接钱包'
  }
})

// 监听路由变化
watch(() => route.params.id, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    await loadTradeDetail()
  }
})
</script>

<style lang="scss" scoped>.trade-detail {
  padding: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .mb-4 {
    margin-bottom: 16px;
  }

  .mt-4 {
    margin-top: 16px;
  }

  .step-header {
    font-weight: 600;
    font-size: 16px;
  }

  .parties-container {
    display: flex;
    gap: 20px;
    margin-top: 10px;

    .party-card {
      flex: 1;
      padding: 15px;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      background-color: #f9fafc;

      h3 {
        margin: 0 0 10px 0;
        color: #303133;
        font-size: 16px;
      }

      p {
        margin: 8px 0;
        color: #606266;
        font-size: 14px;

        strong {
          color: #303133;
        }
      }
    }
  }

  .action-buttons {
    display: flex;
    gap: 10px;
    justify-content: center;
    flex-wrap: wrap;

    :deep(.el-button) {
      min-width: 120px;
      height: 40px;
    }
  }

  .loading-container {
    padding: 40px 0;
    text-align: center;
  }

  .error-container {
    padding: 40px 0;
    text-align: center;

    .error-actions {
      margin-top: 20px;
    }
  }

  .no-data-container {
    padding: 40px 0;
    text-align: center;
  }

  .debug-info {
    margin: 20px 0;
  }
}
</style>