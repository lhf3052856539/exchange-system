// src/composables/useAirdrop.js
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAirdropStore } from '@/stores'

export function useAirdrop() {
    const airdropStore = useAirdropStore()
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

    async function claim() {
        if (processing.value) return

        processing.value = true

        try {
            const result = await airdropStore.claim()
            ElMessage.success('空投领取成功！')
            return result
        } catch (error) {
            console.error('Claim failed:', error)

            // 显示具体的错误信息
            let errorMsg = '领取失败'
            if (error.message) {
                if (error.message.includes('No active airdrop')) {
                    errorMsg = '当前没有活跃的空投活动'
                } else if (error.message.includes('already claimed')) {
                    errorMsg = '您已经领取过空投了'
                } else if (error.message.includes('insufficient')) {
                    errorMsg = '空投池余额不足'
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
