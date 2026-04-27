<template>
  <div class="trade-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的交易</span>
          <el-button @click="handleRefresh">刷新</el-button>
        </div>
      </template>

      <!-- 待上链的撮合交易 -->
      <div v-if="matchedTrades.length > 0" class="matched-section mb-4">
        <h3 style="margin-bottom: 16px; color: #409eff;">待创建链上交易对</h3>
        <el-table
            :data="matchedTrades"
            v-loading="loading"
            style="width: 100%"
            border
        >
          <el-table-column label="交易对" width="150">
            <template #default="{ row }">
              {{ row.fromCurrency }}/{{ row.toCurrency }}
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="金额 (UT)" width="120" />
          <el-table-column label="Party A" width="180">
            <template #default="{ row }">
              <span style="font-family: monospace; font-size: 12px;">{{ formatAddress(row.partyA) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Party B" width="180">
            <template #default="{ row }">
              <span style="font-family: monospace; font-size: 12px;">{{ formatAddress(row.partyB) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag type="warning">已撮合</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="200">
            <template #default="{ row }">
              <el-button
                  v-if="row.partyB === walletStore.address"
                  type="primary"
                  size="small"
                  :loading="creatingOnChain === row.tradeId"
                  @click="handleCreateOnChain(row)"
              >
                创建链上交易对
              </el-button>
              <span v-else style="color: #999; font-size: 12px;">等待 PartyB 操作</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider v-if="matchedTrades.length > 0 && tradeList.length > 0" />

      <el-table
          :data="tradeList"
          v-loading="loading"
          style="width: 100%"
      >
        <el-table-column label="交易对" width="150">
          <template #default="{ row }">
            {{ row.fromCurrency }}/{{ row.toCurrency }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额 (UT)" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.myRole === 'partyA'" type="success">甲方</el-tag>
            <el-tag v-else-if="row.myRole === 'partyB'" type="warning">乙方</el-tag>
            <el-tag v-else type="info">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="300">
          <template #default="{ row }">
            <!-- 授权按钮（甲方在 PARTY_B_CONFIRMED 状态显示） -->
            <el-button
                v-if="row.myRole === 'PARTY_A' && row.status === 5"
                type="warning"
                size="small"
                @click="handleApprove(row)"
            >
              授权手续费
            </el-button>

            <!-- 确认按钮 -->
            <el-button
                v-if="row.status === TRADE_STATUS_CODE.CONFIRMING_A && row.myRole === 'partyA'"
                type="primary"
                size="small"
                @click="handleConfirm(row, true)"
            >
              确认转账
            </el-button>
            <el-button
                v-if="row.status === TRADE_STATUS_CODE.CONFIRMING_B && row.myRole === 'partyB'"
                type="primary"
                size="small"
                @click="handleConfirm(row, false)"
            >
              确认收款
            </el-button>

            <!-- 争议按钮 -->
            <el-button
                v-if="!row.isCompleted && !row.isDisputed && row.myRole !== undefined"
                type="danger"
                size="small"
                @click="handleDispute(row)"
            >
              发起争议
            </el-button>

            <!-- 详情按钮 -->
            <el-button
                size="small"
                @click="viewDetail(row.tradeId)"
            >
              详情
            </el-button>
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
        <el-button type="primary" class="dispute-submit-btn" :loading="submitting" @click="submitDisputeHandler">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTradeStore } from '@/stores'
import { useWalletStore } from '@/stores'
import { useUserStore } from '@/stores/modules/user'
import { useTrade } from '@/composables/useTrade'
import { TRADE_STATUS_CODE } from '@/config/constants'
import { createTradePair, getUserTrades, disputeTrade } from '@/api/trade'
import * as ethers from 'ethers'

const router = useRouter()
const tradeStore = useTradeStore()
const walletStore = useWalletStore()
const userStore = useUserStore()
const { loadTrades, confirmTrade, submitDispute, getStatusTag, getStatusText } = useTrade()

const loading = computed(() => tradeStore.loading)
// 过滤掉状态为 9（待创建链上交易对）的记录
const tradeList = computed(() => tradeStore.tradeList.filter(item => item.status !== 9))
const pagination = computed(() => tradeStore.pagination)

const disputeDialogVisible = ref(false)
const currentTrade = ref(null)
const submitting = ref(false)
const creatingOnChain = ref(null)

const matchedTrades = ref([])

const disputeForm = ref({
  reason: '',
  description: ''
})


async function handleRefresh() {
  await loadTrades()
  await fetchMatchedTrades()
}

async function fetchMatchedTrades() {
  try {
    const walletStore = useWalletStore()
    if (!walletStore.address) return

    const res = await getUserTrades(walletStore.address, 9)
    matchedTrades.value = res.data || []
  } catch (error) {
    console.error('Failed to fetch matched trades:', error)
  }
}

async function handleCreateOnChain(trade) {
  const userAddress = walletStore.address

  if (trade.partyB.toLowerCase() !== userAddress.toLowerCase()) {
    ElMessage.warning('只有 PartyB 可以发起链上创建交易对')
    return
  }

  try {
    creatingOnChain.value = trade.tradeId

    if (!window.ethereum) {
      throw new Error('请安装 MetaMask')
    }

    ElMessage.info('请在钱包中确认创建交易对...')

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    const EXCHANGE_ADDRESS = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    if (!EXCHANGE_ADDRESS) {
      throw new Error('Exchange 合约地址未配置')
    }

    const EXCHANGE_ABI = [
      'function createTradePair(address partyA, address partyB, uint256 amount, uint256 feeAmount, uint256 tradeId) external returns (uint256)'
    ]

    const contract = new ethers.Contract(EXCHANGE_ADDRESS, EXCHANGE_ABI, signer)

    const numericPart = trade.tradeId.replace(/\D/g, '')
    const tradeId = BigInt(numericPart || Date.now())

    const amountWei = ethers.parseUnits(trade.amount.toString(), 6)
    const feeAmount = ethers.parseUnits((Number(trade.amount) / 10000).toString(), 6)

    const tx = await contract.createTradePair(
        trade.partyA,
        trade.partyB,
        amountWei,
        feeAmount,
        tradeId
    )

    console.log('🔄 交易已发送:', tx.hash)
    ElMessage.info('等待交易确认...')

    const receipt = await tx.wait()
    console.log('✅ 交易已确认:', receipt.transactionHash)

    ElMessage.success(`链上交易对创建成功: ${receipt.transactionHash}`)

    await fetchMatchedTrades()
    await loadTrades()
  } catch (error) {
    console.error('Create trade pair failed:', error)
    ElMessage.error(error.message || '创建交易对失败')
  } finally {
    creatingOnChain.value = null
  }
}

async function handleConfirm(row, isPartyA) {
  try {
    const { value: txHash } = await ElMessageBox.prompt(
        '请输入转账交易哈希 (Tx Hash)',
        '确认交易',
        {
          inputValue: '',
          inputPattern: /^(0x)?[0-9a-fA-F]{64}$/,
          inputErrorMessage: '格式不正确，应为 64 位十六进制数（可带 0x 前缀）'
        }
    )

    // 新流程：先调用后端校验，再调用链上合约
    await confirmOnChain(row.tradeId, txHash, isPartyA)
    await loadTrades()
    ElMessage.success('确认成功，等待链上事件同步...')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Confirm failed:', error)
      ElMessage.error('操作失败：' + (error.message || '未知错误'))
    }
  }
}

/**
 * 在链上确认交易（甲方或乙方）
 * 流程：后端校验 → 前端调用链上合约 → 后端监听器同步
 */
async function confirmOnChain(tradeId, txHash, isPartyA) {
  try {
    if (!window.ethereum) {
      throw new Error('请安装 MetaMask')
    }

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()
    const address = await signer.getAddress()

    // 1. 先调用后端进行数据校验
    ElMessage.info('正在进行数据校验...')
    const validateUrl = isPartyA
        ? '/trade/confirm-party-a'
        : '/trade/confirm-party-b'

    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8096/apis'}${validateUrl}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        address,
        tradeId,
        txHash
      })
    })

    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.message || '后端校验失败')
    }

    ElMessage.success('✅ 后端校验通过，开始链上确认...')

    // 2. 调用链上合约进行确认
    const EXCHANGE_ADDRESS = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    if (!EXCHANGE_ADDRESS) {
      throw new Error('Exchange 合约地址未配置')
    }

    const EXCHANGE_ABI = [
      'function confirmPartyA(uint256 chainTradeId, bytes32 txHash) external',
      'function confirmPartyB(uint256 chainTradeId, bytes32 txHash) external',
      'function getTradeInfo(uint256 chainTradeId) view returns (tuple(uint256 id, uint256 chainTradeId, address partyA, address partyB, uint256 amount, uint256 exchangeRate, uint256 state, address disputedParty, uint256 completeTime))'
    ]

    const contract = new ethers.Contract(EXCHANGE_ADDRESS, EXCHANGE_ABI, signer)

    // 查询链上交易 ID
    const tradeInfo = await contract.getTradeInfo(BigInt(tradeId))
    const chainTradeId = tradeInfo.chainTradeId

    // 将 txHash 字符串转换为 bytes32
    const txHashBytes32 = ethers.hexlify(ethers.toUtf8Bytes(txHash.padEnd(64, '0').slice(0, 64)))

    // 调用对应的确认函数
    let tx
    if (isPartyA) {
      tx = await contract.confirmPartyA(chainTradeId, txHashBytes32)
    } else {
      tx = await contract.confirmPartyB(chainTradeId, txHashBytes32)
    }

    ElMessage.info('交易已提交，等待区块确认...')

    const receipt = await tx.wait()
    ElMessage.success(`✅ 链上确认成功！交易哈希：${receipt.transactionHash}`)

    // 3. 后端监听器会自动同步链上事件，刷新页面即可看到最新状态
    console.log('⏳ 等待后端监听器同步链上事件...')
  } catch (error) {
    console.error('Chain confirmation failed:', error)
    throw error
  }
}

function handleDispute(row) {
  currentTrade.value = row
  disputeForm.value = {
    reason: '',
    description: ''
  }
  disputeDialogVisible.value = true
}

async function submitDisputeHandler() {
  if (!disputeForm.value.reason) {
    ElMessage.warning('请选择争议原因')
    return
  }

  // 修复：使用 currentTrade.value 而不是 trade.value
  if (!currentTrade.value?.tradeId) {
    ElMessage.error('交易 ID 为空')
    return
  }

  submitting.value = true
  try {
    const chainTradeId = currentTrade.value.chainTradeId || currentTrade.value.tradeId

    if (!chainTradeId) {
      ElMessage.error('链上交易 ID 不存在，请先创建链上交易对')
      return
    }

    ElMessage.info('正在验证争议信息...')
    const validateRes = await disputeTrade({
      chainTradeId: chainTradeId.toString(),
      reason: disputeForm.value.reason,
      evidence: disputeForm.value.description
    })

    if (!validateRes.data || !validateRes.data.chainTradeId) {
      ElMessage.error('后端校验失败')
      return
    }

    const disputedParty = validateRes.data.accused

    // 2. 校验通过后，前端调用链上合约
    ElMessage.info('正在提交链上争议...')

    const EXCHANGE_ADDRESS = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
    if (!EXCHANGE_ADDRESS) {
      throw new Error('Exchange 合约地址未配置')
    }

    const EXCHANGE_ABI = [
      'function disputeTrade(uint256 tradeId, address disputedParty) external'
    ]

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()
    const contract = new ethers.Contract(EXCHANGE_ADDRESS, EXCHANGE_ABI, signer)

    const tx = await contract.disputeTrade(chainTradeId, disputedParty)
    ElMessage.info('交易已提交，等待区块确认...')

    const receipt = await tx.wait()
    ElMessage.success('争议提交成功！交易哈希：' + receipt.transactionHash)

    disputeDialogVisible.value = false
    await loadTrades()

  } catch (error) {
    console.error('Dispute failed:', error)
    ElMessage.error('提交失败：' + (error.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}


function viewDetail(tradeId) {
  router.push(`/trade/${tradeId}`)
}

function handlePageChange(page) {
  loadTrades({ page })
}

function handleSizeChange(size) {
  pagination.value.page = 1
  loadTrades({ page: 1, size })
}

function formatAddress(address) {
  if (!address) return 'N/A'
  return address.slice(0, 6) + '...' + address.slice(-4)
}

onMounted(async () => {
  if (walletStore.address) {
    await loadTrades()
    await fetchMatchedTrades()
  }
})
</script>



<style lang="scss" scoped>
.trade-list {
  padding: 20px;

  .dispute-submit-btn {
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

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .matched-section {
    animation: fadeIn 0.5s ease-in;
  }

  @keyframes fadeIn {
    from {
      opacity: 0;
      transform: translateY(-10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
