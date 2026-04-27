// src/stores/modules/dao.js
import { defineStore } from 'pinia'
import {
    getProposals,
    getProposalDetail,
    createProposal,
    voteProposal,
    queueProposal,
    executeProposal,
    cancelProposal,
    getVotingPower,
    hasVoted,
    getDaoStats
} from '@/api/dao'
import { useWalletStore } from './wallet'
import { ethers } from 'ethers'

// 提案状态枚举
export const ProposalState = {
    Pending: 0,      // 提案已创建，等待投票中
    Active: 1,       // 投票中
    Succeeded: 2,    // 等待加入公示期
    Failed: 3,       // 提案失败
    Queued: 4,       // 公示期中
    Executed: 5,     // 提案已执行
    Cancelled: 6     // 提案已取消
}

// 提案状态文本映射
export const ProposalStateText = {
    [ProposalState.Pending]: '提案已创建，等待投票中',
    [ProposalState.Active]: '投票中',
    [ProposalState.Succeeded]: '等待加入公示期',
    [ProposalState.Failed]: '提案失败',
    [ProposalState.Queued]: '公示期中',
    [ProposalState.Executed]: '提案已执行',
    [ProposalState.Cancelled]: '提案已取消'
}

// 提案状态标签类型映射
export const ProposalStateTag = {
    [ProposalState.Pending]: 'info',
    [ProposalState.Active]: 'warning',
    [ProposalState.Succeeded]: 'success',
    [ProposalState.Failed]: 'danger',
    [ProposalState.Queued]: 'primary',
    [ProposalState.Executed]: 'success',
    [ProposalState.Cancelled]: 'info'
}

export const useDaoStore = defineStore('dao', {
    state: () => ({
        proposals: [],
        currentProposal: null,
        votingPower: 0,
        daoStats: null,
        loading: false,
        pagination: {
            page: 1,
            size: 10,
            total: 0
        }
    }),

    getters: {
        /**
         * 获取活跃提案（投票中）
         */
        activeProposals: (state) => {
            return state.proposals.filter(p => p.state === ProposalState.Active)
        },

        /**
         * 获取待执行提案（公示期中）
         */
        queuedProposals: (state) => {
            return state.proposals.filter(p => p.state === ProposalState.Queued)
        }
    },

    actions: {
        /**
         * 加载提案列表
         */
        async loadProposals(params = {}) {
            this.loading = true
            try {
                const res = await getProposals(
                    params.page || this.pagination.page,
                    params.size || this.pagination.size
                )
                console.log('📦 Proposal list response:', res)
                console.log('📦 Data field:', res.data)

                // 后端直接返回分页对象，不需要取 .data
                const responseData = res.data || res

                this.proposals = responseData.rows || responseData.list || []
                this.pagination.total = responseData.total || 0

                console.log('✅ Loaded proposals:', this.proposals.length, 'Total:', this.pagination.total)
            } catch (error) {
                console.error('Failed to load proposals:', error)
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * 加载提案详情
         */
        async loadProposalDetail(proposalId) {
            this.loading = true
            try {
                const walletStore = useWalletStore()
                const address = walletStore.address

                console.log('🔍 Loading proposal detail:', proposalId, 'with address:', address)

                // ✅ 修复：request 拦截器返回的是 response.data，即 JsonVO 对象
                // 需要再取 .data 获取实际的 ProposalDTO
                const res = await getProposalDetail(proposalId, address)

                console.log('📦 Proposal detail response:', res)
                console.log('📦 Response data field:', res.data)

                // ✅ res 是 JsonVO 对象，需要取 res.data 获取 ProposalDTO
                this.currentProposal = res.data || res
                return this.currentProposal
            } catch (error) {
                console.error('❌ Failed to load proposal detail:', error)
                console.error('Error details:', error.response?.data || error.message)
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * 创建提案
         */
        async submitProposal(proposalData) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            // 1. 先调用后端做参数校验
            await createProposal(address, proposalData)
            console.log('✅ 后端校验通过，开始链上交易...')

            // 2. 调用链上合约创建提案
            const DAO_ADDRESS = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
            if (!DAO_ADDRESS) {
                throw new Error('DAO 合约地址未配置')
            }

            const DAO_ABI = [
                'function propose(address targetContract, uint256 value, bytes callData, string description) external returns (uint256)'
            ]

            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()
            const contract = new ethers.Contract(DAO_ADDRESS, DAO_ABI, signer)

            console.log('📤 发送链上交易...')
            console.log('目标合约:', proposalData.targetContract)
            console.log('ETH 数量:', proposalData.value)
            console.log('调用数据:', proposalData.callData)
            console.log('描述:', proposalData.description)

            const tx = await contract.propose(
                proposalData.targetContract,
                ethers.parseEther(proposalData.value.toString()),
                proposalData.callData || '0x',
                proposalData.description
            )

            console.log('🔄 交易已发送:', tx.hash)
            console.log('⏳ 等待交易确认...')

            const receipt = await tx.wait()
            console.log('✅ 交易已确认:', receipt.transactionHash)

            // 3. 刷新提案列表（后端监听器会自动同步）
            await this.loadProposals()

            return { txHash: receipt.transactionHash }
        },

        /**
         * 投票
         */
        async submitVote(proposalId, support) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await voteProposal(address, proposalId, support)

            // 刷新提案详情
            await this.loadProposalDetail(proposalId)

            return res.data
        },

        /**
         * 将提案加入公示期
         */
        async submitQueue(proposalId) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            await queueProposal(address, proposalId)
            console.log('✅ 后端校验通过，开始链上交易...')

            const DAO_ADDRESS = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
            if (!DAO_ADDRESS) {
                throw new Error('DAO 合约地址未配置')
            }

            const DAO_ABI = [
                'function queue(uint256 proposalId) external'
            ]

            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()
            const contract = new ethers.Contract(DAO_ADDRESS, DAO_ABI, signer)

            console.log('📤 发送链上 queue 交易...')
            const tx = await contract.queue(proposalId)
            console.log('🔄 交易已发送:', tx.hash)

            const receipt = await tx.wait()
            console.log('✅ 交易已确认:', receipt.transactionHash)

            await this.loadProposalDetail(proposalId)

            return { txHash: receipt.transactionHash }
        },

        /**
         * 执行提案
         */
        async submitExecute(proposalId) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            await executeProposal(address, proposalId)
            console.log('✅ 后端校验通过，开始链上交易...')

            const DAO_ADDRESS = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
            if (!DAO_ADDRESS) {
                throw new Error('DAO 合约地址未配置')
            }

            const DAO_ABI = [
                'function execute(uint256 proposalId) external payable'
            ]

            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()
            const contract = new ethers.Contract(DAO_ADDRESS, DAO_ABI, signer)

            console.log('📤 发送链上 execute 交易...')
            const tx = await contract.execute(proposalId)
            console.log('🔄 交易已发送:', tx.hash)

            const receipt = await tx.wait()
            console.log('✅ 交易已确认:', receipt.transactionHash)

            await this.loadProposalDetail(proposalId)

            return { txHash: receipt.transactionHash }
        },

        /**
         * 取消提案
         */
        async submitCancel(proposalId) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await cancelProposal(address, proposalId)
            await this.loadProposalDetail(proposalId)
            return res.data
        },

        /**
         * 获取投票权
         */
        async fetchVotingPower() {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                return
            }

            try {
                const res = await getVotingPower(address)
                this.votingPower = res.data
            } catch (error) {
                console.error('Failed to fetch voting power:', error)
            }
        },

        /**
         * 检查是否已投票
         */
        async checkHasVoted(proposalId) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                return false
            }

            try {
                const res = await hasVoted(proposalId, address)
                return res.data
            } catch (error) {
                console.error('Failed to check voted status:', error)
                return false
            }
        },

        /**
         * 获取 DAO 统计信息
         */
        async fetchDaoStats() {
            try {
                const res = await getDaoStats()
                this.daoStats = res.data
            } catch (error) {
                console.error('Failed to fetch dao stats:', error)
            }
        },

        /**
         * 清空当前提案
         */
        clearCurrentProposal() {
            this.currentProposal = null
        }
    }
})
