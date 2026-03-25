// src/stores/modules/airdrop.js
import { defineStore } from 'pinia'
import {
    claimAirdrop,
    hasClaimed,
    getAirdropInfo
} from '@/api/airdrop'
import { useWalletStore } from './wallet'

export const useAirdropStore = defineStore('airdrop', {
    state: () => ({
        airdropInfo: null,
        hasClaimedStatus: false,
        loading: false
    }),

    getters: {
        fixedAmount: (state) => state.airdropInfo?.fixedAmount || 0,
        totalAirdropAmount: (state) => state.airdropInfo?.totalAirdropAmount || 0,
        claimedAmount: (state) => state.airdropInfo?.claimedAmount || 0,
        remainingAmount: (state) => state.airdropInfo?.remainingAmount || 0
    },

    actions: {
        async checkClaimedStatus() {
            this.loading = true
            try {
                const walletStore = useWalletStore()
                const address = walletStore.address

                if (!address) {
                    throw new Error('Wallet not connected')
                }

                const res = await hasClaimed(address)
                this.hasClaimedStatus = res.data
            } catch (error) {
                console.error('Failed to check claimed status:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        async fetchAirdropInfo() {
            this.loading = true
            try {
                const walletStore = useWalletStore()
                const address = walletStore.address

                if (!address) {
                    throw new Error('Wallet not connected')
                }

                const res = await getAirdropInfo(address)
                this.airdropInfo = res.data
            } catch (error) {
                console.error('Failed to fetch airdrop info:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        async claim() {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await claimAirdrop(address)
            await this.fetchAirdropInfo()
            await this.checkClaimedStatus()
            return res.data
        },

        clearAirdrop() {
            this.airdropInfo = null
            this.hasClaimedStatus = false
        }
    }
})
