<template>
  <div class="assets-page">
    <div class="page-header">
      <el-button
          type="info"
          size="small"
          :icon="Back"
          @click="$router.push('/home')"        style="margin-right: 12px;"
      >
        返回
      </el-button>
      <h2 class="page-title">我的资产</h2>
    </div>

    <el-card class="assets-content" shadow="never">
      <div v-loading="loading">
        <el-descriptions title="账户信息" :column="1" border>
          <el-descriptions-item label="钱包地址">
            {{ walletStore.shortAddress || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户类型">
            <el-tag :type="userTypeTag">{{ userStore.userInfo?.userTypeDesc }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="EXTH 余额">
            {{ userStore.userInfo?.exthBalance || 0 }} EXTH
          </el-descriptions-item>
          <el-descriptions-item label="可交易 UT">
            {{ userStore.userInfo?.tradeableUt || 0 }} UT
          </el-descriptions-item>
          <el-descriptions-item label="新用户剩余次数">
            {{ userStore.userInfo?.newUserTradeCount || 0 }} 次
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
            title="提示"
            type="info"
            description="EXTH 余额来自链上查询，可能存在延迟。请确保已连接到正确的网络。"
            show-icon            style="margin-top: 20px"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>import { computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/modules/user'
import { useWalletStore } from '@/stores/modules/wallet'
import { Back } from '@element-plus/icons-vue'

const userStore = useUserStore()
const walletStore = useWalletStore()

const loading = computed(() => userStore.loading)

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
  return textMap[userStore.userType] || '未知'
})

onMounted(async () => {
  if (!userStore.userInfo && walletStore.address) {
    await userStore.fetchUserInfo(walletStore.address)
  }
})
</script>

<style lang="scss" scoped>.assets-page {
  .page-header {
    display: flex;
    align-items: center;
    margin-bottom: 20px;

    .page-title {
      font-size: 20px;
      font-weight: 600;
      color: #303133;
      margin: 0;
    }
  }

  .assets-content {
    min-height: 400px;
    border-radius: 8px;
  }
}
</style>