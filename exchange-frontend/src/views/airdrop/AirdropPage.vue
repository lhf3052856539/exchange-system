<template>
  <div class="airdrop-page">
    <el-row :gutter="20">
      <el-col :span="12" :offset="6">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>空投领取</span>
            </div>
          </template>

          <div v-loading="loading" class="content">
            <el-result
                v-if="!hasActiveAirdrop"
                icon="info"
                title="暂无空投活动"
                sub-title="当前没有活跃的空投活动，敬请期待"
            >
              <template #extra>
                <el-button class="home-btn" @click="$router.push('/home')">
                  返回首页
                </el-button>
              </template>
            </el-result>

            <el-result
                v-else-if="hasClaimedStatus"
                icon="success"
                title="已领取空投"
                sub-title="您已经领取过空投奖励了"
            >
              <template #extra>
                <el-button type="primary" @click="$router.push('/trade/list')">
                  查看交易
                </el-button>
              </template>
            </el-result>

            <el-result
                v-else-if="!isInWhitelist"
                icon="warning"
                title="很遗憾"
                sub-title="您的地址不在本次空投白名单中"
            >
              <template #extra>
                <el-button type="primary" @click="$router.push('/home')">
                  返回首页
                </el-button>
              </template>
            </el-result>

            <div v-else>
              <el-statistic
                  title="您可领取金额"
                  :value="userAirdropData ? (parseFloat(userAirdropData.amount) || userAirdropData.amount) : 0"
                  suffix="EXTH"
              />
              <el-statistic
                  title="剩余可领取总量"
                  :value="parseFloat(airdropStore.airdropInfo?.data?.totalAirdrop || 0)"
                  suffix="EXTH"                  style="margin-top: 20px"
              />

              <el-button
                  type="primary"
                  class="claim-btn"
                  :loading="processing"                  style="width: 100%; margin-top: 30px"
                  @click="handleClaim"
              >
                立即领取
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAirdropStore } from '@/stores/modules/airdrop'
import { useWalletStore } from '@/stores/modules/wallet'
import { useAirdrop } from '@/composables/useAirdrop'
import merkleData from '../../../merkle-output.json'

const airdropStore = useAirdropStore()
const walletStore = useWalletStore()
const { processing, hasClaimedStatus, fixedAmount, remainingAmount, loading, claim } = useAirdrop()

const userAirdropData = ref(null)

const hasActiveAirdrop = computed(() => {
  const res = airdropStore.airdropInfo
  const info = res?.data || res

  console.log('🔍 hasActiveAirdrop debug:', {
    info,
    isActive: info?.isActive,
    totalAirdrop: info?.totalAirdrop,
    parsedTotal: parseFloat(info?.totalAirdrop || 0)
  })

  const total = parseFloat(info?.totalAirdrop || 0)

  return info &&
      info.isActive === true &&
      total > 0
})

const isInWhitelist = computed(() => {
  return userAirdropData.value !== null
})

async function handleClaim() {
  if (!userAirdropData.value) {
    ElMessage.error('您不在空投白名单中')
    return
  }

  try {
    await claim(userAirdropData.value.amount, userAirdropData.value.proof)
    ElMessage.success('空投领取成功！')
  } catch (error) {
    console.error('Failed to claim airdrop:', error)
  }
}

onMounted(async () => {
  if (!walletStore.isConnected) {
    ElMessage.warning('请先连接钱包')
    return
  }

  try {
    await Promise.all([
      airdropStore.fetchAirdropInfo(),
      airdropStore.checkClaimedStatus()
    ])

    const res = airdropStore.airdropInfo
    const info = res.data || res

    if (info && info.canClaim === true) {
      const walletAddress = walletStore.address.toLowerCase()

      const claimData = Object.entries(merkleData.claims).find(
          ([key]) => key.toLowerCase() === walletAddress
      )?.[1]

      if (claimData) {
        userAirdropData.value = {
          amount: claimData.amount,
          proof: claimData.proof
        }
        console.log('✅ User found in merkle-output.json:', userAirdropData.value)
      } else {
        userAirdropData.value = null
        console.log('⚠️ Address not found, wallet:', walletAddress)
        console.log('📋 Available addresses:', Object.keys(merkleData.claims))
      }
    } else {
      userAirdropData.value = null
    }
  } catch (error) {
    console.error('❌ Failed to load airdrop info:', error)
    ElMessage.error('加载空投信息失败')
  }
})
</script>




<style lang="scss" scoped>
.airdrop-page {
  padding: 40px 20px;

  .home-btn {
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

  .claim-btn {
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
</style>
