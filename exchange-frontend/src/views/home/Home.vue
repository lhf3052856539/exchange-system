<!-- src/views/home/Home.vue -->
<template>
  <div class="home-page">
    <!-- 快捷操作移到最上面 -->
    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="24">
        <el-card class="quick-actions">
          <template #header>
            <span class="card-title">快捷操作</span>
          </template>

          <div class="action-buttons">
            <el-button
                type="primary"
                size="large"
                class="start-trade-btn"
                @click="$router.push('/trade/request')"
            >
              <el-icon><Connection /></el-icon>
              发起交易
            </el-button>

            <el-button
                type="success"
                size="large"
                @click="$router.push('/airdrop')"
            >
              <el-icon><Coin /></el-icon>
              领取空投
            </el-button>

            <el-button
                type="warning"
                size="large"
                @click="$router.push('/dao')"
            >
              <el-icon><List /></el-icon>
              DAO 治理
            </el-button>

            <el-button
                type="info"
                size="large"
                @click="$router.push('/trade/list')"
            >
              <el-icon><Document /></el-icon>
              交易记录
            </el-button>

            <el-button
                type="danger"
                size="large"
                @click="$router.push('/profile/assets')"
            >
              <el-icon><Wallet /></el-icon>
              资产管理
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="asset-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">EXTH 余额</span>
              <el-icon><Coin /></el-icon>
            </div>
          </template>

          <div class="asset-value">
            {{ formatNumber(userStore.userInfo?.exthBalance || 0) }}
          </div>
          <div class="asset-unit">EXTH</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="asset-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">可交易 UT</span>
              <el-icon><Ticket /></el-icon>
            </div>
          </template>

          <div class="asset-value">
            {{ formatNumber(userStore.userInfo?.tradeableUt || 0) }}
          </div>
          <div class="asset-unit">UT</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="asset-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">用户类型</span>
              <el-icon><User /></el-icon>
            </div>
          </template>

          <div class="asset-value">
            <el-tag :type="userTypeTag" size="large">
              {{ userStore.userInfo?.userTypeDesc }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <page-container title="交易统计" class="stats-container">
          <div v-if="userStore.tradeStats" class="stats-list">
            <div class="stat-item">
              <span class="label">总交易数</span>
              <span class="value">{{ userStore.tradeStats.totalTrades }}</span>
            </div>

            <div class="stat-item">
              <span class="label">成功交易</span>
              <span class="value success">{{ userStore.tradeStats.completedTrades }}</span>
            </div>

            <div class="stat-item">
              <span class="label">成功率</span>
              <el-progress
                  :percentage="calculateSuccessRate(userStore.tradeStats)"
                  :stroke-width="20"
                  :show-text="false"
                  color="#409EFF"
              />
            </div>

            <div class="stat-item">
              <span class="label">总奖励</span>
              <span class="value primary">{{ userStore.tradeStats.totalReward }} EXTH</span>
            </div>
          </div>
          <div v-else class="empty-data">暂无数据</div>
        </page-container>
      </el-col>

      <el-col :span="12">
        <page-container title="实时汇率" class="rates-container">
          <template #actions>
            <el-button
                type="primary"
                size="small"
                :icon="Refresh"
                @click="fetchRates"
            >
              刷新
            </el-button>
          </template>

          <el-table
              :data="ratesList"
              stripe
              size="small"
              :show-header="true"
          >
            <el-table-column prop="pair" label="货币对" width="120" />
            <el-table-column prop="rate" label="汇率" width="150">
              <template #default="{ row }">
                <span class="rate-value">{{ row.rate }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" />
          </el-table>
          <div v-if="ratesList.length === 0" class="empty-data">暂无数据</div>
        </page-container>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useUserStore } from '@/stores'
import { useWalletStore } from '@/stores/modules/wallet'
import { formatNumber } from '@/utils/format'
import { getRates } from '@/api/rate'
import {
  Connection, Coin, List, Document, Wallet, Ticket, User, Refresh
} from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import wsClient from '@/utils/websocket'

const userStore = useUserStore()
const walletStore = useWalletStore() // 使用钱包 store 获取 address
const ratesList = ref([])

function calculateSuccessRate(stats) {
  if (!stats || stats.totalTrades === 0) return 0
  return Math.round((stats.completedTrades / stats.totalTrades) * 100)
}

const userTypeTag = computed(() => {
  const typeMap = {
    '新用户': 'success',
    '普通用户': 'primary',
    '种子用户': 'warning'
  }
  return typeMap[userStore.userInfo?.userTypeDesc] || 'info'
})

const userTypeText = computed(() => {
  const textMap = {
    NEW: '新用户',
    NORMAL: '普通用户',
    SEED: '种子用户'
  }
  return textMap[userStore.userType] || userStore.userInfo?.userTypeDesc || '未知'
})

async function refreshUserInfo() {
  try {
    console.log('🔄 Starting to fetch user data with address:', walletStore.address)

    if (!walletStore.address) {
      console.error('❌ Wallet address is empty!')
      return
    }

    const userInfoResult = await userStore.fetchUserInfo(walletStore.address)
    console.log('✅ User info result:', userInfoResult)
    console.log('✅ User info stored:', userStore.userInfo)

    const tradeStatsResult = await userStore.fetchTradeStats(walletStore.address)
    console.log('✅ Trade stats result:', tradeStatsResult)
    console.log('✅ Trade stats stored:', userStore.tradeStats)

    await fetchRates()

  } catch (error) {
    console.error('❌ Failed to refresh data:', error)
  }
}
async function fetchRates() {
  try {
    const res = await getRates()
    console.log('📊 Rates API response:', res)

    // res 本身就是数据对象
    const rates = res || {}
    if (rates && Object.keys(rates).length > 0) {
      ratesList.value = [
        { pair: 'USD/CNY', rate: rates.usdToCny || '0.00', updateTime: rates.timestamp || new Date().toLocaleString() },
        { pair: 'USD/GBP', rate: rates.usdToGbp || '0.00', updateTime: rates.timestamp || new Date().toLocaleString() },
        { pair: 'CNY/USD', rate: rates.cnyToUsd || '0.00', updateTime: rates.timestamp || new Date().toLocaleString() },
        { pair: 'GBP/USD', rate: rates.gbpToUsd || '0.00', updateTime: rates.timestamp || new Date().toLocaleString() }
      ]
      console.log('✅ Rates list set:', ratesList.value)
    } else {
      console.warn('⚠️ No rates data available')
      ratesList.value = []
    }
  } catch (error) {
    console.error('❌ Failed to fetch rates:', error)
    ratesList.value = []
  }
}

// 监听交易更新事件
function handleTradeUpdate(event) {
  console.log('🔄 Trade updated, refreshing home data...', event.detail)
  refreshUserInfo()
}

onMounted(() => {
  const walletStore = useWalletStore()

  // 如果 store 中没有地址，尝试从 localStorage 恢复
  if (!walletStore.address) {
    const savedAddress = localStorage.getItem('walletAddress')
    if (savedAddress) {
      walletStore.setWallet(savedAddress)
      console.log('📍 从 localStorage 恢复钱包地址:', savedAddress)
    }
  }

  console.log('📍 当前钱包地址:', walletStore.address)

  if (walletStore.address) {
    wsClient.connect(walletStore.address)
    refreshUserInfo()
  } else {
    console.warn('🟡 钱包未连接，等待用户注册/登录')
  }
})

onUnmounted(() => {
  // 移除事件监听
  window.removeEventListener('trade-updated', handleTradeUpdate)
})
</script>

<style lang="scss" scoped>.home-page {
  .card-title {
    font-weight: bold;
    text-align: center;
    display: block;
    width: 100%;
  }

  .asset-card {
    text-align: center;
    padding: 20px 0;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      color: #606266;
      font-size: 14px;

      .card-title {
        flex: 1;
        text-align: center;
      }
    }

    .asset-value {
      font-size: 32px;
      font-weight: bold;
      color: #409EFF;
      margin: 20px 0 8px;
    }

    .asset-unit {
      font-size: 14px;
      color: #909399;
    }
  }

  .stats-container, .rates-container {
    height: auto;

    :deep(.page-header__title) {
      font-weight: bold;
      text-align: center;
    }
  }


  .stats-list {
    .stat-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid #f0f0f0;

      &:last-child {
        border-bottom: none;
      }

      .label {
        color: #606266;
        font-size: 14px;
      }

      .value {
        font-size: 18px;
        font-weight: bold;

        &.success {
          color: #67C23A;
        }

        &.primary {
          color: #409EFF;
        }
      }
    }
  }

  .quick-actions {
    :deep(.el-card__header) {
      text-align: center;

      .card-title {
        display: inline-block;
        width: 100%;
      }
    }

    .action-buttons {
      display: flex;
      gap: 16px;
      justify-content: center;
      flex-wrap: wrap;

      .start-trade-btn {
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
  }

  .empty-data {
    text-align: center;
    padding: 40px 0;
    color: #909399;
    font-size: 14px;
  }
}
</style>