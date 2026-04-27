// src/stores/modules/airdrop.js
import { defineStore } from 'pinia'
import {
    claimAirdrop,
    hasClaimed,
    getAirdropInfo
} from '@/api/airdrop'
import { useWalletStore } from './wallet'
import { ethers } from 'ethers'
import web3 from '@/utils/web3'

const AIRDROP_CONTRACT_ADDRESS = import.meta.env.VITE_AIRDROP_CONTRACT_ADDRESS
const AIRDROP_ABI = [
    "function claim(uint256 amount, bytes32[] calldata merkleProof) external"
]

export const useAirdropStore = defineStore('airdrop', {
    state: () => ({
        airdropInfo: null,
        hasClaimedStatus: false,
        loading: false
    }),

    getters: {
        fixedAmount: (state) => state.airdropInfo?.perAddress || 0,
        totalAirdropAmount: (state) => state.airdropInfo?.totalAirdrop || 0,
        claimedAmount: (state) => state.airdropInfo?.claimedAmount || 0,
        remainingAmount: (state) => state.airdropInfo?.remainingAmount || 0,
        isActive: (state) => state.airdropInfo && state.airdropInfo.totalAirdrop > 0
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
                console.log('Backend response:', res)

                this.airdropInfo = res
                console.log('Store airdropInfo set to:', this.airdropInfo)
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

            console.log(' Step 1: Validating with backend...')
            const validationRes = await claimAirdrop(address)

            if (!validationRes.data || !validationRes.data.validated) {
                throw new Error('Backend validation failed')
            }

            console.log('Backend validated, Step 2: Calling chain contract...')
            const amount = validationRes.data.amount
            const merkleProof = validationRes.data.merkleProof

            if (!web3.signer) {
                await web3.connect()
            }

            const airdropContract = new ethers.Contract(
                AIRDROP_CONTRACT_ADDRESS,
                AIRDROP_ABI,
                web3.signer
            )

            const tx = await airdropContract.claim(amount, merkleProof)
            console.log('Transaction sent:', tx.hash)

            const receipt = await tx.wait()
            console.log('Transaction confirmed:', receipt.transactionHash)

            await this.fetchAirdropInfo()
            await this.checkClaimedStatus()

            return receipt.transactionHash
        },

        clearAirdrop() {
            this.airdropInfo = null
            this.hasClaimedStatus = false
        }
    }
})

