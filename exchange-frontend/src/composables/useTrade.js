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
            10: 'info',      // WAITING_MATCH
            0: 'warning',   // MATCHED   -交易已创建，等待率先转账方转账
            1: 'success',   // PARTY_A_CONFIRMED - 等待履约方确认转账
            2: 'success',   // PARTY_B_CONFIRMED - 等待率先转账方确认

            3: 'success',   // COMPLETED - 交易已完成
            4: 'info' ,     // CANCELLED - 交易已取消
            5: 'danger',    // DISPUTED - 交易存在争议
            6: 'info',      //FAILED  - 交易争议已解决
            7: 'info',      // EXPIRED - 交易已过期

            8: 'warning'    // PENDING_CHAIN_CONFIRM - 等待链上确认
        }

        return statusMap[status] || 'info'
    }

    function getStatusText(status) {
        // 支持数字和字符串两种格式
        const textMap = {
            10: '待匹配',          // WAITING_MATCH
            0: '交易已创建，等待率先转账方转账',          // MATCHED
            1: '等待履约方确认转账',     // PARTY_A_CONFIRMED
            2: '等待率先转账方最终确认', // PARTY_B_CONFIRMED
            3: '交易已完成',          // COMPLETED
            4: '交易已取消',          // CANCELLED
            5: '交易存在争议',          // DISPUTED
            6: '交易争议已解决',          // FAILED
            7: '已过期',          // EXPIRED

            8: '等待链上确认',      // PENDING_CHAIN_CONFIRM
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
