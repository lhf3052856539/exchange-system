<!-- src/components/wallet/BalanceDisplay.vue -->
<template>
  <div class="balance-display" :class="{ 'loading': loading }">
    <div class="balance-label">
      <slot name="label">
        <el-icon><Wallet /></el-icon>
        {{ label }}
      </slot>
    </div>
    <div class="balance-value">
      {{ formattedBalance }}
      <span class="balance-symbol">{{ symbol }}</span>
    </div>
    <div v-if="showApproxValue" class="approx-value">
      ≈ ${{ approxUsdValue }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Wallet } from '@element-plus/icons-vue'
import { formatNumber, fromChainUnit } from '@/utils/format'

const props = defineProps({
  balance: {
    type: [String, Number],
    default: '0'
  },
  symbol: {
    type: String,
    default: 'ETH'
  },
  label: {
    type: String,
    default: '余额'
  },
  decimals: {
    type: Number,
    default: 6
  },
  showApproxValue: {
    type: Boolean,
    default: false
  },
  usdPrice: {
    type: [Number, String],
    default: 0
  },
  loading: {
    type: Boolean,
    default: false
  }
})


const formattedBalance = computed(() => {
  return fromChainUnit(props.balance, props.decimals)
})

const approxUsdValue = computed(() => {
  const readableBalance = Number(fromChainUnit(props.balance, props.decimals)) || 0
  const price = Number(props.usdPrice) || 0
  return (readableBalance * price).toFixed(2)
})
</script>

<style lang="scss" scoped>
.balance-display {
  &.loading {
    opacity: 0.6;
  }

  .balance-label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    color: #909399;
    margin-bottom: 8px;
  }

  .balance-value {
    font-size: 24px;
    font-weight: bold;
    color: #409EFF;

    .balance-symbol {
      font-size: 16px;
      margin-left: 4px;
    }
  }

  .approx-value {
    font-size: 12px;
    color: #C0C4CC;
    margin-top: 4px;
  }
}
</style>
