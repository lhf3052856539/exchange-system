// src/composables/useWeb3.js
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useWalletStore } from '@/stores'
import { connectWallet as connectWalletUtil, checkNetwork } from '@/utils/web3'
import { CHAIN_CONFIG } from '@/config/web3Config'

export function useWeb3() {
    const walletStore = useWalletStore()
    const connecting = ref(false)

    const isConnected = computed(() => walletStore.isConnected)
    const address = computed(() => walletStore.address)
    const chainId = computed(() => walletStore.chainId)

    async function connectWallet() {
        if (connecting.value) return

        connecting.value = true

        try {
            const result = await connectWalletUtil()

            walletStore.setWallet(result.address, result.chainId)

            // 检查网络是否正确
            const networkOk = await checkNetwork()
            if (!networkOk) {
                ElMessage.warning('当前网络不正确，请切换到测试网')
            }

            ElMessage.success('钱包连接成功')
            return result
        } catch (error) {
            ElMessage.error('连接失败：' + error.message)
            throw error
        } finally {
            connecting.value = false
        }
    }

    async function disconnectWallet() {
        walletStore.clearWallet()
        ElMessage.success('已断开连接')
    }

    async function switchNetwork() {
        try {
            await window.ethereum.request({
                method: 'wallet_switchEthereumChain',
                params: [{ chainId: CHAIN_CONFIG.chainId }],
            })

            const newChainId = await window.ethereum.request({
                method: 'eth_chainId'
            })

            walletStore.setChainId(newChainId)
            ElMessage.success('网络切换成功')
        } catch (error) {
            ElMessage.error('切换网络失败：' + error.message)
            throw error
        }
    }

    return {
        connecting,
        isConnected,
        address,
        chainId,
        connectWallet,
        disconnectWallet,
        switchNetwork
    }

    async function disconnectWallet() {
        walletStore.clearWallet()
        ElMessage.success('已断开连接')
    }

    async function switchNetwork() {
        try {
            await window.ethereum.request({
                method: 'wallet_switchEthereumChain',
                params: [{ chainId: CHAIN_CONFIG.chainId }],
            })

            const newChainId = await window.ethereum.request({
                method: 'eth_chainId'
            })

            walletStore.setChainId(newChainId)
            ElMessage.success('网络切换成功')
        } catch (error) {
            ElMessage.error('切换网络失败：' + error.message)
            throw error
        }
    }

    async function checkConnection() {
        const connected = await web3Util.isConnected()

        if (connected) {
            const accounts = await window.ethereum.request({
                method: 'eth_accounts'
            })

            if (accounts.length > 0) {
                walletStore.setConnected(accounts[0])
                await walletStore.refreshBalance()
                return true
            }
        }

        return false
    }

    return {
        connecting,
        isConnected,
        address,
        chainId,
        connectWallet,
        disconnectWallet,
        switchNetwork,
        checkConnection
    }

}
