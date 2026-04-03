<template>
  <div class="trade-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的交易</span>
          <el-button @click="handleRefresh">刷新</el-button>
        </div>
      </template>

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
        <el-table-column prop="amount" label="金额 (USD)" width="100" />
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
            <el-tag v-else type="warning">乙方</el-tag>
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
                v-if="row.status === TRADE_STATUS_CODE.CONFIRMING_A && row.myRole === 'PARTY_A'"
                type="primary"
                size="small"
                @click="handleConfirm(row, true)"
            >
              确认转账
            </el-button>
            <el-button
                v-if="row.status === TRADE_STATUS_CODE.CONFIRMING_B && row.myRole === 'PARTY_B'"
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
import { useTrade } from '@/composables/useTrade'
import { TRADE_STATUS_CODE } from '@/config/constants'

const router = useRouter()
const tradeStore = useTradeStore()
const walletStore = useWalletStore()
const { loadTrades, confirmTrade, submitDispute, getStatusTag, getStatusText } = useTrade()

const loading = computed(() => tradeStore.loading)
const tradeList = computed(() => tradeStore.tradeList)
const pagination = computed(() => tradeStore.pagination)

const disputeDialogVisible = ref(false)
const currentTrade = ref(null)
const submitting = ref(false)

const disputeForm = ref({
  reason: '',
  description: ''
})

async function handleRefresh() {
  await loadTrades()
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

    await confirmTrade(row.tradeId, txHash, isPartyA)
    await loadTrades()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Confirm failed:', error)
    }
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

async function submitDisputeHandler(event) {
  console.log('=== submitDisputeHandler called ===')
  console.log('Event:', event)
  console.log('currentTrade:', currentTrade.value)
  console.log('tradeId:', currentTrade.value?.tradeId)

  if (!disputeForm.value.reason) {
    ElMessage.warning('请选择争议原因')
    return
  }

  if (!currentTrade.value?.tradeId) {
    ElMessage.error('交易 ID 为空')
    return
  }

  const payload = {
    tradeId: currentTrade.value.tradeId,
    reason: disputeForm.value.reason,
    evidence: disputeForm.value.description
  }

  console.log('Payload to send:', payload)
  console.log('Stringified:', JSON.stringify(payload))

  submitting.value = true
  try {
    await submitDispute(payload)
    disputeDialogVisible.value = false
    await loadTrades()
    ElMessage.success('争议提交成功')
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

onMounted(async () => {
  if (walletStore.address) {
    await loadTrades()
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
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
