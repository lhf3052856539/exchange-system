// src/composables/useAirdrop.js
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAirdropStore } from '@/stores/modules/airdrop'
import { useWalletStore } from '@/stores/modules/wallet'
import { claimAirdrop as claimAirdropApi } from '@/api/airdrop'

export function useAirdrop() {
    const airdropStore = useAirdropStore()
    const walletStore = useWalletStore()
    const processing = ref(false)

    const airdropInfo = computed(() => airdropStore.airdropInfo)
    const hasClaimedStatus = computed(() => airdropStore.hasClaimedStatus)
    const loading = computed(() => airdropStore.loading)
    const fixedAmount = computed(() => airdropStore.fixedAmount)
    const remainingAmount = computed(() => airdropStore.remainingAmount)

    async function checkClaimed() {
        try {
            await airdropStore.checkClaimedStatus()
        } catch (error) {
            ElMessage.error('检查空投状态失败')
            throw error
        }
    }

    async function fetchInfo() {
        try {
            await airdropStore.fetchAirdropInfo()
        } catch (error) {
            ElMessage.error('获取空投信息失败')
            throw error
        }
    }

    async function claim(amount, merkleProof) {
        if (processing.value) return

        processing.value = true

        try {
            const address = walletStore.address

            if (!address) {
                throw new Error('钱包未连接')
            }

            const result = await claimAirdropApi(address, amount.toString(), merkleProof)

            await airdropStore.fetchAirdropInfo()
            await airdropStore.checkClaimedStatus()

            ElMessage.success('空投领取成功！')
            return result.data
        } catch (error) {
            console.error('Claim failed:', error)

            let errorMsg = '领取失败'
            if (error.response?.data?.message) {
                errorMsg = error.response.data.message
            } else if (error.message) {
                if (error.message.includes('No active airdrop')) {
                    errorMsg = '当前没有活跃的空投活动'
                } else if (error.message.includes('already claimed')) {
                    errorMsg = '您已经领取过空投了'
                } else if (error.message.includes('insufficient')) {
                    errorMsg = '空投池余额不足'
                } else if (error.message.includes('not in whitelist')) {
                    errorMsg = '您不在空投白名单中'
                } else {
                    errorMsg = error.message
                }
            }

            ElMessage.error(errorMsg)
            throw error
        } finally {
            processing.value = false
        }
    }

    return {
        airdropInfo,
        hasClaimedStatus,
        loading,
        fixedAmount,
        remainingAmount,
        processing,
        checkClaimed,
        fetchInfo,
        claim
    }
}
