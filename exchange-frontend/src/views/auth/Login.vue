<template>
  <div class="login-container">
    <el-card class="login-card">
      <h1 class="title">去中心化交易系统</h1>
      <p class="subtitle">连接钱包开始交易</p>

      <el-button
          type="primary"
          size="large"
          :loading="connecting"
          @click="connectWallet"
          class="connect-btn"
      >
        {{ connecting ? '连接中...' : '连接 MetaMask' }}
      </el-button>

      <div class="tips">
        <el-alert
            title="温馨提示"
            type="info"
            :closable="false"
            show-icon
        >
          <p>需要使用 MetaMask 或其他兼容的钱包</p>
          <p>首次使用需要注册账户</p>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import { ethers } from 'ethers'
import { ElMessage } from 'element-plus'
import { register, getUserInfo, login } from '@/api/user' // 添加 login 函数
import request from '@/utils/request'
import { useWalletStore } from '@/stores/modules/wallet'

const router = useRouter()
const userStore = useUserStore()
const walletStore = useWalletStore()
const connecting = ref(false)

const connectWallet = async () => {
  if (!window.ethereum) {
    ElMessage.error('请安装 MetaMask 钱包')
    return
  }

  connecting.value = true

  try {
    const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' })

    if (!accounts || accounts.length === 0) {
      throw new Error('未获取到账户，请在 MetaMask 中授权访问')
    }

    const address = accounts[0]
    localStorage.setItem('address', address)
    localStorage.setItem('wallet_address', address)

    const provider = new ethers.BrowserProvider(window.ethereum)
    const signer = await provider.getSigner()

    const message = `Welcome to Exchange System\n\nClick to sign in with your address: ${address}`
    const signature = await signer.signMessage(message)
    localStorage.setItem('signature', signature)

    // 先执行登录接口获取token
    let token = null
    try {
      console.log('尝试登录获取token...')
      const loginRes = await login(address, signature)

      // 处理不同格式的响应
      token = typeof loginRes === 'string' ? loginRes : (loginRes.data || loginRes.token || loginRes)

      if (token) {
        localStorage.setItem('token', token)
        console.log('✅ 登录成功，获取到token')
      } else {
        console.log('⚠️ 登录接口返回但无token，可能是新用户')
      }
    } catch (loginError) {
      console.error('登录失败:', loginError)
      // 登录失败可能是因为新用户，继续注册流程
    }

    // 如果没有token，说明是新用户，执行注册
    if (!token) {
      try {
        console.log('新用户，执行注册流程...')
        const registerRes = await register(address)

        // 注册成功后应该已经有token
        if (registerRes && registerRes.token) {
          token = registerRes.token
          localStorage.setItem('token', token)
        }

        console.log('✅ 注册成功')
      } catch (registerError) {
        console.error('注册失败:', registerError)
        ElMessage.error('注册或登录失败，请重试')
        return
      }
    }

    // 确保有 token 后，再加载用户信息
    if (token) {
      // 设置请求头中的 token
      request.defaults.headers.common['Authorization'] = `Bearer ${token}`

      // 设置钱包地址到 store
      const walletStore = useWalletStore()
      walletStore.setWallet(address)
      localStorage.setItem('walletAddress', address)

      // 加载用户信息
      await userStore.fetchUserInfo()
      ElMessage.success('连接成功')

      setTimeout(() => {
        router.push('/home')
      }, 500)
    } else {
      ElMessage.warning('登录成功，但无法获取完整功能')
      setTimeout(() => {
        router.push('/home')
      }, 500)
    }

  } catch (error) {
    console.error('连接钱包失败:', error)
    ElMessage.error('连接钱包失败')
  } finally {
    connecting.value = false
  }
}
</script>


<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  text-align: center;
}

.title {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
}

.subtitle {
  font-size: 16px;
  color: #666;
  margin-bottom: 30px;
}

.connect-btn {
  width: 100%;
  height: 50px;
  font-size: 18px;
}

.tips {
  margin-top: 20px;
}
</style>
