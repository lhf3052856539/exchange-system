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
          <el-descriptions-item label="金额 (USD)">
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
              v-if="isPartyA && getStatus === TRADE_STATUS.PARTY_B_CONFIRMED"
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
              <p><strong>转账哈希:</strong> {{ trade.partyATxHash || '待确认' }}</p>
              <p><strong>状态:</strong>
                <el-tag v-if="trade.partyATxHash" type="success">已转账</el-tag>
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
        <p><strong>状态:</strong> {{ arbitrationInfo.status }}</p>
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

  // 如果状态是数字，转换为对应的字符串
  if (typeof trade.value.status === 'number') {
    const statusMap = {
      0: TRADE_STATUS.PENDING,
      1: TRADE_STATUS.MATCHED,
      2: TRADE_STATUS.CONFIRMING_A,
      3: TRADE_STATUS.PARTY_A_CONFIRMED,
      4: TRADE_STATUS.CONFIRMING_B,
      5: TRADE_STATUS.PARTY_B_CONFIRMED,
      6: 'PENDING_CHAIN_CONFIRM',  // 待链上确认
      7: TRADE_STATUS.COMPLETED,   // 已完成
      8: TRADE_STATUS.DISPUTED,    // 争议中
      9: TRADE_STATUS.FAILED,      // 失败
      10: TRADE_STATUS.CANCELLED   // 已取消
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
  // 乙方在 CONFIRMING_A 状态下确认收款（等待甲方转账）
  if (getStatus.value === TRADE_STATUS.CONFIRMING_A && !isPartyA.value) {
    return true
  }
  // 乙方在 MATCHED 状态下确认收款（等待甲方转账）
  if (getStatus.value === TRADE_STATUS.MATCHED && !isPartyA.value) {
    return true
  }
  // 甲方在 CONFIRMING_B 状态下确认收款（等待乙方转账）
  if (getStatus.value === TRADE_STATUS.CONFIRMING_B && isPartyA.value) {
    return true
  }
  // 乙方在 PARTY_A_CONFIRMED 状态下确认收款（等待乙方确认）
  if (getStatus.value === TRADE_STATUS.PARTY_A_CONFIRMED && !isPartyA.value) {
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
      status: '投票中',
      executed: false,
      rejected: false
    }

    // 设置进度步骤
    if (arbitrationInfo.value.executed) {
      arbitrationStep.value = 4
    } else if (arbitrationInfo.value.rejected) {
      arbitrationStep.value = 2
    } else if (arbitrationInfo.value.yesVotes >= 2) {
      arbitrationStep.value = 3
    } else {
      arbitrationStep.value = 2
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
  isConfirmingTransfer.value = true
  confirmForm.value.txHash = ''
  confirmDialogVisible.value = true
}

function handleConfirmReceipt() {
  isConfirmingTransfer.value = false
  confirmForm.value.txHash = ''
  confirmDialogVisible.value = true
}

async function handleFinalConfirm() {
  try {
    await ElMessageBox.confirm(
        '请确认您已收到乙方的转账，确认后将完成整个交易。',
        '最终确认',
        {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    confirming.value = true
    try {
      // 调用 store 中的最终确认方法
      await tradeStore.finalConfirmPartyA(trade.value.tradeId)
      await loadTradeDetail() // ✅ 已有刷新
      ElMessage.success('交易已完成')
    } catch (error) {
      console.error('Final confirm failed:', error)
      ElMessage.error('操作失败：' + (error.message || '未知错误'))
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
    await confirmTrade(
        trade.value.tradeId,
        confirmForm.value.txHash,
        isPartyA.value
    )
    confirmDialogVisible.value = false
    await loadTradeDetail() // ✅ 已有刷新
    ElMessage.success('操作成功')
  } catch (error) {
    console.error('Confirm failed:', error)
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
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
    tradeId: trade.value.tradeId,
    reason: disputeForm.value.reason,
    evidence: disputeForm.value.description
  }

  submittingDispute.value = true
  try {
    await submitDispute(payload)
    disputeDialogVisible.value = false
    await loadTradeDetail() // ✅ 已有刷新
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
  if (newTrade && isPartyA.value && getStatus.value === TRADE_STATUS.PARTY_B_CONFIRMED) {
    await checkAllowanceStatus()
  }
}, { immediate: true })

// 检查授权状态（直接读取链上数据）
async function checkAllowanceStatus() {
  try {
    if (!window.ethereum) {
      console.error('MetaMask not installed')
      return
    }

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    // EXTH 合约 ABI（简化版）
    const exthABI = [
      "function allowance(address owner, address spender) view returns (uint256)",
      "function decimals() view returns (uint8)"
    ]

    const exthContract = new ethers.Contract(
        import.meta.env.VITE_EXTH_CONTRACT_ADDRESS,
        exthABI,
        signer
    )

    const allowanceValue = await exthContract.allowance(
        walletStore.address,
        import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS || '0xf7674eB800475D17973F743964ab9f38A43df761'
    )

    allowance.value = allowanceValue.toString()

    // EXTH 是 6 位精度，计算手续费：0.05 EXTH = 50000 (最小单位)
    const feeAmount = BigInt(50000) // 0.05 EXTH (6 位精度)

    needApprove.value = allowanceValue < feeAmount
    approvalConfirmed.value = !needApprove.value

    console.log('Allowance check:', {
      allowance: allowance.value,
      feeAmount: feeAmount.toString(),
      needApprove: needApprove.value,
      spender: import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    })

    if (!needApprove.value) {
      ElMessage.success('授权已生效！')
    }
  } catch (err) {
    console.error('Failed to check allowance:', err)
    // ElMessage.error('查询授权失败')
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

    await ElMessageBox.confirm(
        '需要授权 Exchange 合约使用您的 EXTH 代币支付手续费，是否继续？',
        '授权确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    // EXTH 合约 ABI
    const exthABI = [
      "function approve(address spender, uint256 amount) returns (bool)",
      "function decimals() view returns (uint8)"
    ]

    const exthContract = new ethers.Contract(
        import.meta.env.VITE_EXTH_CONTRACT_ADDRESS,
        exthABI,
        signer
    )

    // EXTH 是 6 位精度，授权 1 EXTH = 1,000,000
    const amount = ethers.parseUnits('1', 6) // 1 EXTH (6 位精度)

    console.log('Approving:', {
      spender: import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS,
      amount: amount.toString()
    })

    const tx = await exthContract.approve(
        import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS || '0xf7674eB800475D17973F743964ab9f38A43df761',
        amount
    )

    console.log('Approve tx sent:', tx.hash)
    ElMessage.info('等待交易确认中...')

    // 等待交易被打包
    const receipt = await tx.wait()

    console.log('Approve confirmed:', receipt.transactionHash)
    ElMessage.success('授权成功！')

    // 重新检查授权状态
    await checkAllowanceStatus()

    // 如果已授权，提示用户可以进行最终确认
    if (!needApprove.value) {
      ElMessage.success('授权已生效，现在可以点击"确认完成交易"按钮')
    } else {
      ElMessage.warning('授权额度不足，请重试')
    }

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

    await loadDetail(route.params.id)

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