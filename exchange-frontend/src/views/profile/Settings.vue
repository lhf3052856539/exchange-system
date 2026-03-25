<template>
  <el-card>
    <template #header>
      <span>设置</span>
    </template>

    <div class="settings-content">
      <el-form label-width="120px">
        <el-form-item label="网络配置">
          <el-input
              v-model="nodeUrl"
              placeholder="http://127.0.0.1:8545"
              disabled
          />
          <div class="form-tip">当前连接的区块链节点地址</div>
        </el-form-item>

        <el-form-item label="钱包地址">
          <el-input
              v-model="walletStore.address"
              disabled
          />
        </el-form-item>

        <el-form-item label="退出登录">
          <el-button type="danger" @click="handleLogout">
            退出登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWalletStore } from '@/stores/modules/wallet'
import { useUserStore } from '@/stores/modules/user'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const walletStore = useWalletStore()
const userStore = useUserStore()

const nodeUrl = ref('http://127.0.0.1:8545')

function handleLogout() {
  ElMessageBox.confirm(
      '确定要退出登录吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('wallet_address')
    userStore.clearUser()
    walletStore.disconnect()
    router.push('/login')
  })
}
</script>

<style lang="scss" scoped>
.settings-content {
  min-height: 300px;

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
