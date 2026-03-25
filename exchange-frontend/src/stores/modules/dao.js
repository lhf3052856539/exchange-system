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

// 提案状态枚举
export const ProposalState = {
    Pending: 0,      // 待开始
    Active: 1,       // 投票中
    Succeeded: 2,    // 投票通过
    Failed: 3,       // 失败
    Queued: 4,       // 已入队列
    Executed: 5,     // 已执行
    Cancelled: 6     // 已取消
}

// 提案状态文本映射
export const ProposalStateText = {
    [ProposalState.Pending]: '待开始',
    [ProposalState.Active]: '投票中',
    [ProposalState.Succeeded]: '已通过',
    [ProposalState.Failed]: '已失败',
    [ProposalState.Queued]: '公示期中',
    [ProposalState.Executed]: '已执行',
    [ProposalState.Cancelled]: '已取消'
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

                // ✅ 修复：request 拦截器已经返回了 res.data，不需要再取 .data
                const res = await getProposalDetail(proposalId, address)

                console.log('📦 Proposal detail response:', res)

                // ✅ res 就是 ProposalDTO 对象，不是 JsonVO 对象
                this.currentProposal = res
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

            const res = await createProposal(address, proposalData)
            return res.data
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

            const res = await queueProposal(address, proposalId)
            await this.loadProposalDetail(proposalId)
            return res.data
        },

        /**
         * 执行提案
         */
        async submitExecute(proposalId, eta) {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                throw new Error('Wallet not connected')
            }

            const res = await executeProposal(address, proposalId, eta)
            await this.loadProposalDetail(proposalId)
            return res.data
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
