// src/composables/useTrade.js
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTradeStore } from '@/stores'
import { useWalletStore } from '@/stores'
import { TRADE_STATUS } from '@/config/constants'

export function useTrade() {
    const tradeStore = useTradeStore()
    const walletStore = useWalletStore()
    const processing = ref(false)

    const tradeList = computed(() => tradeStore.tradeList)
    const currentTrade = computed(() => tradeStore.currentTrade)
    const loading = computed(() => tradeStore.loading)
    const pagination = computed(() => tradeStore.pagination)

    async function loadTrades(params = {}) {
        try {
            await tradeStore.loadTrades(params)
        } catch(error) {
            ElMessage.error('加载交易列表失败')
            throw error
        }
    }

    async function loadDetail(tradeId) {
        try {
            return await tradeStore.loadTradeDetail(tradeId)
        } catch (error) {
            ElMessage.error('加载交易详情失败')
            throw error
        }
    }

    async function submitRequest(data) {
        if (processing.value) return

        processing.value = true

        try {
            const result = await tradeStore.submitRequest(data)
            ElMessage.success('交易请求已提交')
            return result
        } catch (error) {
            ElMessage.error('提交交易请求失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    async function confirmTrade(tradeId, txHash, isPartyA = true) {
        if (processing.value) return

        processing.value = true

        try {
            if (isPartyA) {
                await tradeStore.confirmPartyATransaction(tradeId, txHash)
            } else {
                await tradeStore.confirmPartyBTransaction(tradeId, txHash)
            }

            ElMessage.success('确认成功')
            await loadDetail(tradeId)
        } catch (error) {
            ElMessage.error('确认失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    async function submitDispute(param) {
        if (processing.value) return

        processing.value = true

        try {
            const result = await tradeStore.submitDispute(param)
            ElMessage.success('争议已提交')
            return result
        } catch (error) {
            ElMessage.error('提交争议失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    function getStatusTag(status) {
        // 支持数字和字符串两种格式
        const statusMap = {
            0: 'info',      // WAITING_MATCH
            1: 'warning',   // MATCHED
            2: 'primary',   // CONFIRMING_A - 等待甲方提交哈希
            3: 'success',   // PARTY_A_CONFIRMED - 甲方已确认
            4: 'primary',   // CONFIRMING_B - 等待乙方提交哈希
            5: 'success',   // PARTY_B_CONFIRMED - 乙方已确认
            6: 'success',   // COMPLETED
            7: 'danger',    // DISPUTED
            8: 'info',      // EXPIRED
            9: 'info'       // CANCELLED
        }

        return statusMap[status] || 'info'
    }

    function getStatusText(status) {
        // 支持数字和字符串两种格式
        const textMap = {
            0: '待匹配',          // WAITING_MATCH
            1: '已匹配',          // MATCHED
            2: '等待甲方转账',     // CONFIRMING_A - 等待甲方提交哈希
            3: '等待乙方确认',     // PARTY_A_CONFIRMED - 甲方已确认，等待乙方
            4: '等待乙方转账',     // CONFIRMING_B - 等待乙方提交哈希
            5: '等待甲方最终确认', // PARTY_B_CONFIRMED - 乙方已确认，等待甲方
            6: '已完成',          // COMPLETED
            7: '争议中',          // DISPUTED
            8: '已过期',          // EXPIRED
            9: '已取消'           // CANCELLED
        }

        return textMap[status] || status
    }

    return {
        tradeList,
        currentTrade,
        loading,
        pagination,
        processing,
        loadTrades,
        loadDetail,
        submitRequest,
        confirmTrade,
        submitDispute,
        getStatusTag,
        getStatusText
    }
}
