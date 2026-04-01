import { defineStore } from 'pinia'
import { getCommitteeMembers, checkCommitteeMember, getProposals } from '@/api/arbitration'

export const useArbitrationStore = defineStore('arbitration', {
    state: () => ({
        isCommitteeMember: false,
        committeeMembers: [],
        proposals: [],
        currentProposal: null,
        loading: false,
        error: null
    }),

    getters: {
        // 获取待处理的提案
        pendingProposals: (state) => {
            return state.proposals.filter(p => !p.executed && !p.rejected)
        },

        // 获取已执行的提案
        executedProposals: (state) => {
            return state.proposals.filter(p => p.executed)
        },

        // 获取已拒绝的提案
        rejectedProposals: (state) => {
            return state.proposals.filter(p => p.rejected)
        }
    },

    actions: {
        async checkCommitteeStatus() {
            try {
                const res = await checkCommitteeMember()
                this.isCommitteeMember = res.data
                return res.data
            } catch (error) {
                console.error('Failed to check committee status:', error)
                this.isCommitteeMember = false
                return false
            }
        },

        async loadCommitteeMembers() {
            try {
                this.loading = true
                const res = await getCommitteeMembers()
                this.committeeMembers = res.data
                this.loading = false
            } catch (error) {
                this.error = error.message
                this.loading = false
                throw error
            }
        },

        async loadProposals(page = 1, pageSize = 10, status = null) {
            try {
                this.loading = true
                const res = await getProposals(page, pageSize, status)
                this.proposals = res.data.rows || res.data
                this.loading = false
            } catch (error) {
                this.error = error.message
                this.loading = false
                throw error
            }
        }
    }
})
