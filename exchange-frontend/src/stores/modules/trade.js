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

            // 获取交易详情以获取 chainTradeId
            const trade = await this.loadTradeDetail(transactionId)
            const chainTradeId = trade.chainTradeId

            if (!chainTradeId) {
                throw new Error('Chain trade ID not found')
            }

            // 直接调用链上合约 completeTrade
            const { ethers } = await import('ethers')
            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()

            const exchangeABI = [
                "function completeTrade(uint256 tradeId) external returns (bool)"
            ]

            const exchangeAddress = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
            const exchangeContract = new ethers.Contract(exchangeAddress, exchangeABI, signer)

            console.log('Calling chain completeTrade with chainTradeId:', chainTradeId)

            const tx = await exchangeContract.completeTrade(chainTradeId)
            console.log('Transaction sent:', tx.hash)

            await tx.wait()
            console.log('Chain completeTrade confirmed:', tx.hash)

            // 等待后端监听器同步状态
            await new Promise(resolve => setTimeout(resolve, 2000))
            await this.loadTradeDetail(transactionId)

            return trade
        },

        async submitDispute(param) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            // 获取交易详情以获取 chainTradeId
            const trade = await this.loadTradeDetail(param.tradeId)
            const chainTradeId = trade.chainTradeId

            if (!chainTradeId) {
                throw new Error('Chain trade ID not found')
            }

            // 确定被争议方
            const accusedParty = address === trade.partyA ? trade.partyB : trade.partyA

            // 直接调用链上合约 disputeTrade
            const { ethers } = await import('ethers')
            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()

            const exchangeABI = [
                "function disputeTrade(uint256 tradeId, address disputedParty) external"
            ]

            const exchangeAddress = import.meta.env.VITE_EXCHANGE_CONTRACT_ADDRESS
            const exchangeContract = new ethers.Contract(exchangeAddress, exchangeABI, signer)

            console.log('Calling chain disputeTrade with chainTradeId:', chainTradeId, 'accusedParty:', accusedParty)

            const tx = await exchangeContract.disputeTrade(chainTradeId, accusedParty)
            console.log('Transaction sent:', tx.hash)

            await tx.wait()
            console.log('Chain disputeTrade confirmed:', tx.hash)

            // 等待后端监听器同步状态
            await new Promise(resolve => setTimeout(resolve, 2000))
            await this.loadTradeDetail(param.tradeId)

            return trade
        },

        clearCurrentTrade() {
            this.currentTrade = null
        }
    }
})
