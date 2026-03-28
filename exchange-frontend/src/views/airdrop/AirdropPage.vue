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
                  :value="userAirdropData ? parseFloat(userAirdropData.amount) / 1e6 : 0"
                  suffix="EXTH"
              />
              <el-statistic
                  title="剩余可领取总量"
                  :value="remainingAmount"
                  :suffix="'EXTH'"                  style="margin-top: 20px"
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
import { generateMerkleProof } from '@/utils/merkleTree'
import whitelistData from '../../../../exc-contracts/whitelist.json'

const airdropStore = useAirdropStore()
const walletStore = useWalletStore()
const { processing, hasClaimedStatus, fixedAmount, remainingAmount, loading, claim } = useAirdrop()

// 用户的空投资格
const userAirdropData = ref(null)

// 检查是否有活跃的空投活动
const hasActiveAirdrop = computed(() => {
  const info = airdropStore.airdropInfo
  return info &&
      info.isActive === true &&
      info.totalAirdrop > 0 &&
      info.remainingAmount > 0
})

// 检查用户是否在白名单中
const isInWhitelist = computed(() => {
  return userAirdropData.value !== null
})

// 处理领取空投
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
    // 错误消息已经在 useAirdrop 中处理
  }
}

// 页面加载时获取空投信息
onMounted(async () => {
  if (!walletStore.isConnected) {
    ElMessage.warning('请先连接钱包')
    return
  }

  try {
    // 并行执行两个请求
    await Promise.all([
      airdropStore.fetchAirdropInfo(),
      airdropStore.checkClaimedStatus()
    ])

    // 查询用户是否在白名单中
    const address = walletStore.address
    try {
      const { amount, proof } = generateMerkleProof(address, whitelistData)
      userAirdropData.value = { amount, proof }

      console.log('✅ User airdrop data:', {
        address,
        amount: amount.toString(),
        proofLength: proof.length
      })
    } catch (error) {
      console.log('⚠️ User not in whitelist or error:', error.message)
      userAirdropData.value = null
    }

    console.log('✅ Airdrop info loaded:', airdropStore.airdropInfo)
    console.log('✅ Has claimed status:', airdropStore.hasClaimedStatus)
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
