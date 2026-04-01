// src/stores/modules/trade.js
import { defineStore } from 'pinia'
import {
    requestMatch,
    confirmPartyA,
    confirmPartyB,
    getTradeDetail,
    getUserTrades,
    disputeTrade
} from '@/api/trade'
import { useWalletStore } from './wallet'

export const useTradeStore = defineStore('trade', {
    state: () => ({
        tradeList: [],
        currentTrade: null,
        loading: false,
        pagination: {
            page: 1,
            size: 10,
            total: 0
        }
    }),

    actions: {
        async loadTrades(queryParams = {}) {
            this.loading = true
            try {
                const walletStore = useWalletStore()
                const address = walletStore.address

                console.log('=== Store loadTrades ===')
                console.log('Address:', address)
                console.log('Query params:', queryParams)

                if (!address) {
                    console.warn('Wallet not connected')
                    this.tradeList = []
                    this.pagination.total = 0
                    return
                }

                // 调用 API
                const res = await getUserTrades(
                    address,
                    queryParams.status || '',
                    queryParams.page || 1,
                    queryParams.size || this.pagination.size
                )
                console.log('Trade list response:', res)

                // 修复：API 返回的是 {code: 200, data: [...]}，需要取 data 字段
                this.tradeList = Array.isArray(res.data) ? res.data : []
                this.pagination.total = this.tradeList.length

                console.log('Trade list loaded:', this.tradeList.length, 'items')
            } catch (error) {
                console.error('Failed to load trades:', error)
                throw error
            } finally {
                this.loading = false
            }
        },
        async loadTradeDetail(tradeId) {
            this.loading = true
            try {
                console.log('=== Store loadTradeDetail ===')
                console.log('Trade ID:', tradeId)

                const res = await getTradeDetail(tradeId)
                console.log('Get trade detail response:', res)

                // 后端返回的格式是 JsonVO，需要取 data 字段
                this.currentTrade = res.data || res
                console.log('Current trade:', this.currentTrade)

                return this.currentTrade
            } catch (error) {
                console.error('Failed to load trade detail:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        async submitRequest(data) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await requestMatch(address, data)
            return res.data
        },

        async confirmPartyATransaction(tradeId, txHash) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await confirmPartyA(address, tradeId, txHash)
            await this.loadTradeDetail(tradeId)
            return res.data
        },

        async confirmPartyBTransaction(tradeId, txHash) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await confirmPartyB(address, tradeId, txHash)
            await this.loadTradeDetail(tradeId)
            return res.data
        },

        async finalConfirmPartyA(transactionId) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            // 调用最终确认接口 - 使用完整 URL
            const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8096/apis'
            const response = await fetch(`${apiBaseUrl}/trade/final-confirm-party-a?tradeId=${transactionId}`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('token')}`,
                    'Content-Type': 'application/json'
                }
            })

            if (!response.ok) {
                const errorData = await response.json()
                throw new Error(errorData.message || '操作失败')
            }

            const res = await response.json()
            await this.loadTradeDetail(transactionId)
            return res.data
        },

        async submitDispute(param) {
            const res = await disputeTrade(param)
            return res.data
        },

        clearCurrentTrade() {
            this.currentTrade = null
        }
    }
})
