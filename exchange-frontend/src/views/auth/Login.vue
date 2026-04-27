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
import { login } from '@/api/user'
import request from '@/utils/request'
import { useWalletStore } from '@/stores/modules/wallet'
import { WEB3_CONFIG } from '@/config/web3Config'

// Exchange 合约地址
const EXCHANGE_ADDRESS = WEB3_CONFIG.contractAddresses.Exchange

// Exchange 合约最小 ABI (仅 registerUser)
const EXCHANGE_ABI = [
  'function registerUser() external'
]

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

// 尝试登录
    let token = null
    try {
      console.log('尝试登录获取token...')
      const loginRes = await login(address, signature)
      console.log('登录接口完整响应:', JSON.stringify(loginRes))

      // 检查后端返回的业务状态码
      if (loginRes?.code === 400 && loginRes?.message === 'NOT_REGISTERED') {
        console.log('用户未注册，开始链上注册...')
        await registerOnChain(address, provider, signer)

        // 注册成功后重新登录
        console.log('注册完成，重新登录...')
        const loginRes2 = await login(address, signature)
        console.log('重新登录响应:', JSON.stringify(loginRes2))

        if (loginRes2?.code !== 200) {
          throw new Error('登录后获取 token 失败')
        }

        token = loginRes2.data
        localStorage.setItem('token', token)
      } else if (loginRes?.code === 200) {
        // 正常登录成功
        token = loginRes.data
        localStorage.setItem('token', token)
        console.log('✅ 登录成功，获取到token')
      } else {
        throw new Error(loginRes?.message || '登录失败')
      }
    } catch (loginError) {
      console.error('登录流程异常:', loginError)

      // 情况1: axios 网络层错误（有 response 属性）
      if (loginError.response) {
        const errorMsg = loginError.response.data?.message
        if (errorMsg === 'NOT_REGISTERED') {
          console.log('捕获到 NOT_REGISTERED 错误，开始链上注册...')
          await registerOnChain(address, provider, signer)

          // 注册成功后重新登录
          console.log('注册完成，重新登录...')
          const loginRes = await login(address, signature)

          if (loginRes?.code !== 200) {
            throw new Error('登录后获取 token 失败')
          }

          token = loginRes.data
          localStorage.setItem('token', token)
        } else {
          ElMessage.error('登录失败: ' + (errorMsg || '未知错误'))
          return
        }
      }
// 情况2: 业务逻辑抛出的 Error 对象（检查 message）
      else if (loginError.message === 'NOT_REGISTERED') {
        console.log('捕获到 NOT_REGISTERED 异常，开始链上注册...')
        await registerOnChain(address, provider, signer)

        // 注册成功后重新登录
        console.log('注册完成，重新登录...')
        const loginRes = await login(address, signature)
        console.log('重新登录响应:', JSON.stringify(loginRes))

        if (loginRes?.code !== 200) {
          console.error('登录响应 code 不为 200:', loginRes)
          throw new Error('登录后获取 token 失败')
        }

        token = loginRes.data
        console.log('提取到的 token:', token ? token.substring(0, 20) + '...' : 'null')
        localStorage.setItem('token', token)
      }
      else {
        // 其他错误
        ElMessage.error(loginError.message || '登录失败，请重试')
        return
      }
    }


    // 设置 token 和钱包信息
    if (token) {
      walletStore.setWallet(address)
      localStorage.setItem('walletAddress', address)

      // 加载用户信息
      await userStore.fetchUserInfo(address)
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
    ElMessage.error(error.message || '连接钱包失败')
  } finally {
    connecting.value = false
  }
}

// 链上注册用户
const registerOnChain = async (address, provider, signer) => {
  try {
    ElMessage.info('正在链上注册用户...')

    const contract = new ethers.Contract(EXCHANGE_ADDRESS, EXCHANGE_ABI, signer)

    const tx = await contract.registerUser()
    console.log('注册交易已发送:', tx.hash)

    ElMessage.info('等待交易确认...')
    const receipt = await tx.wait()

    console.log('注册成功:', receipt)
    ElMessage.success('链上注册成功')

  } catch (error) {
    console.error('链上注册失败:', error)

    // 用户取消交易
    if (error.code === 'ACTION_REJECTED') {
      throw new Error('用户取消了注册交易')
    }

    // 已注册过
    if (error.message.includes('Already registered')) {
      console.log('用户已在链上注册')
      return
    }

    throw new Error('链上注册失败: ' + (error.message || error.reason))
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
