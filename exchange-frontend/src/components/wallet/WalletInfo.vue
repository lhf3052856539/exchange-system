<!-- src/components/wallet/WalletInfo.vue -->
<template>
  <div class="wallet-info">
    <div class="info-header">
      <el-avatar :size="48" icon="Wallet" />
      <div class="address-section">
        <div class="address-title">
          {{ shortAddress }}
          <el-tag v-if="isConnected" type="success" size="small">已连接</el-tag>
        </div>
        <div class="full-address" v-if="showFullAddress">
          {{ address }}
        </div>
      </div>
    </div>

    <el-divider />

    <div class="info-content">
      <div class="info-item">
        <span class="item-label">网络</span>
        <span class="item-value">
          <el-tag :type="networkTag" size="small">
            {{ networkName }}
          </el-tag>
        </span>
      </div>

      <div class="info-item">
        <span class="item-label">Chain ID</span>
        <span class="item-value">{{ chainId }}</span>
      </div>

      <div v-if="showBalance" class="info-item">
        <span class="item-label">余额</span>
        <span class="item-value">{{ balance }} ETH</span>
      </div>
    </div>

    <div v-if="showActions" class="info-actions">
      <el-button size="small" @click="handleCopy">
        <el-icon><CopyDocument /></el-icon>
        复制地址
      </el-button>
      <el-button
          size="small"
          type="danger"
          plain
          @click="handleDisconnect"
      >
        断开连接
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument, Wallet } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useWalletStore } from '@/stores'

const props = defineProps({
  showFullAddress: {
    type: Boolean,
    default: false
  },
  showBalance: {
    type: Boolean,
    default: true
  },
  showActions: {
    type: Boolean,
    default: true
  }
})

const walletStore = useWalletStore()

const address = computed(() => walletStore.address)
const isConnected = computed(() => walletStore.isConnected)
const shortAddress = computed(() => walletStore.shortAddress)
const chainId = computed(() => walletStore.chainId)
const balance = computed(() => walletStore.balance)

const networkName = computed(() => {
  const chainMap = {
    '0x1': 'Ethereum Mainnet',
    '0x5': 'Goerli Testnet',
    '0xaa36a7': 'Sepolia Testnet',
    '0x89': 'Polygon',
  }
  return chainMap[chainId.value] || `Chain ${chainId.value}`
})

const networkTag = computed(() => {
  if (chainId.value === '0x1') return 'success'
  if (['0x5', '0xaa36a7'].includes(chainId.value)) return 'warning'
  return 'info'
})

function handleCopy() {
  navigator.clipboard.writeText(address.value)
  ElMessage.success('地址已复制')
}

function handleDisconnect() {
  walletStore.disconnect()
  ElMessage.info('已断开连接')
}
</script>

<style lang="scss" scoped>
.wallet-info {
  padding: 16px;

  .info-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;

    .address-section {
      flex: 1;

      .address-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 16px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 4px;
      }

      .full-address {
        font-size: 12px;
        color: #909399;
        font-family: monospace;
        word-break: break-all;
      }
    }
  }

  .info-content {
    .info-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 0;

      &:not(:last-child) {
        border-bottom: 1px solid #f0f0f0;
      }

      .item-label {
        color: #606266;
        font-size: 14px;
      }

      .item-value {
        color: #303133;
        font-weight: 500;
      }
    }
  }

  .info-actions {
    margin-top: 16px;
    display: flex;
    gap: 12px;
    justify-content: space-between;
  }
}
</style>
