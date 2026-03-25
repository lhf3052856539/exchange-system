// src/utils/web3.js
import { ethers } from 'ethers'
import { ElMessage } from 'element-plus'

class Web3Util {
  constructor() {
    this.provider = null
    this.signer = null
    this.address= null
  }

  async connect() {
    if (!window.ethereum) {
      ElMessage.error('请安装 MetaMask 钱包')
      throw new Error('MetaMask not installed')
    }

    try {
     const accounts = await window.ethereum.request({
       method: 'eth_requestAccounts'
      })

      this.provider = new ethers.BrowserProvider(window.ethereum)
      this.signer = await this.provider.getSigner()
      this.address= accounts[0]

     return {
        provider: this.provider,
        signer: this.signer,
       address: this.address
      }
    } catch (error) {
     console.error('Failed to connect:', error)
      throw error
    }
  }

  async isConnected() {
    if (!window.ethereum) return false

    try {
     const accounts = await window.ethereum.request({
       method: 'eth_accounts'
      })
     return accounts.length > 0
    } catch {
     return false
    }
  }

  async signMessage(message) {
    if (!this.signer) {
      throw new Error('请先连接钱包')
    }

   return await this.signer.signMessage(message)
  }

  async sendTransaction(to, value, data) {
    if (!this.signer) {
      throw new Error('请先连接钱包')
    }

   const tx = {
      to,
      value,
     data
    }

   const txResponse = await this.signer.sendTransaction(tx)
   return await txResponse.wait()
  }

  async getBalance(address) {
    if (!this.provider) {
      throw new Error('请先连接钱包')
    }

   const balance = await this.provider.getBalance(address)
   return ethers.formatEther(balance)
  }

    async switchChain(chainId) {
        if (!window.ethereum) {
            throw new Error('MetaMask not installed')
        }

        try {
            // 如果 chainId 是十进制，转换为 hex；如果已经是 hex，直接使用
            const hexChainId = typeof chainId === 'number'
                ? `0x${chainId.toString(16)}`
                : chainId

            await window.ethereum.request({
                method: 'wallet_switchEthereumChain',
                params: [{ chainId: hexChainId }],
            })
        } catch (switchError) {
            if (switchError.code === 4902) {
                ElMessage.error('该链未在钱包中配置')
            }
            throw switchError
        }
    }
}
export default new Web3Util()
