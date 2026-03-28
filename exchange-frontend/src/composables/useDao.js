// src/composables/useDao.js
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDaoStore, ProposalState, ProposalStateText, ProposalStateTag } from '@/stores'
import { useWalletStore } from '@/stores'
import { hasVoted } from '@/api/dao'
import { ethers } from 'ethers'

export { ProposalState, ProposalStateText, ProposalStateTag }

export function useDao() {
    const daoStore = useDaoStore()
    const processing = ref(false)

    const proposals = computed(() => daoStore.proposals)
    const currentProposal = computed(() => daoStore.currentProposal)
    const votingPower = computed(() => daoStore.votingPower)
    const daoStats = computed(() => daoStore.daoStats)
    const loading = computed(() => daoStore.loading)
    const pagination = computed(() => daoStore.pagination)

    async function loadProposals(params = {}) {
        try {
            await daoStore.loadProposals(params)
        } catch(error) {
            ElMessage.error('加载提案列表失败')
            throw error
        }
    }

    async function createProposal(data) {
        const res = await api.post('/dao/proposal/create', data)

        // 🔁 立即本地添加一条“待确认”记录
        store.commit('ADD_TEMP_PROPOSAL', {
            ...data,
            txHash: res.data.txHash,
            status: 'pending',
            createdAt: new Date()
        })

        ElMessage.success('提案已提交，等待网络确认...')
        return res
    }

    function pollProposalStatus(txHash) {
        const interval = setInterval(async () => {
            try {
                const status = await fetchProposalStatusByTx(txHash)
                if (status === 'confirmed') {
                    clearInterval(interval)
                    updateLocalProposal(txHash, { status: 'active' })
                    ElMessage.success('提案已确认，进入投票阶段')
                }
            } catch (err) {
                console.error(err)
            }
        }, 6000)
    }


    async function loadProposalDetail(proposalId) {
        try {
            return await daoStore.loadProposalDetail(proposalId)
        } catch (error) {
            ElMessage.error('加载提案详情失败')
            throw error
        }
    }

    async function submitProposal(data) {
        if (processing.value) return

        processing.value = true

        try {
            const result = await daoStore.submitProposal(data)
            ElMessage.success('提案创建成功！')
            return result
        } catch (error) {
            ElMessage.error('创建提案失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    async function submitVote(proposalId, support) {
        if (processing.value) return

        processing.value = true

        try {
            const walletStore = useWalletStore()

            if (!walletStore.address) {
                throw new Error('请先连接钱包')
            }

            // 获取 DAO 合约地址
            const daoAddress = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
            if (!daoAddress) {
                throw new Error('DAO 合约地址未配置')
            }

            console.log('=== 开始链上投票 ===')
            console.log('提案 ID:', proposalId)
            console.log('支持:', support)
            console.log('DAO 合约地址:', daoAddress)
            console.log('用户地址:', walletStore.address)

            // DAO 合约的 vote 方法 ABI
            const daoABI = [
                'function vote(uint256 _proposalId, bool _support) external'
            ]

            // 创建合约实例
            const provider = new ethers.BrowserProvider(window.ethereum)
            const signer = await provider.getSigner()
            const contract = new ethers.Contract(daoAddress, daoABI, signer)

            console.log('准备调用合约方法...')

            // 调用投票方法
            const tx = await contract.vote(proposalId, support)

            console.log('交易已发送:', tx.hash)
            console.log('等待交易确认...')

            // 等待交易确认
            const receipt = await tx.wait()

            console.log('交易已确认:', receipt.transactionHash)

            ElMessage.success('投票成功！')

            // 刷新提案详情
            try {
                await loadProposalDetail(proposalId)
            } catch (err) {
                console.error('Failed to refresh proposal:', err)
            }

            return { txHash: receipt.transactionHash }
        } catch (error) {
            console.error('Vote error:', error)

            if (error.code === 'ACTION_REJECTED') {
                ElMessage.warning('您取消了投票操作')
            } else if (error.reason && error.reason.includes('No voting power')) {
                ElMessage.error({
                    message: '您在提案创建时没有投票权，无法参与投票！请在下次创建提案前先委托投票权！',
                    duration: 6000
                })
            } else if (error.message && error.message.includes('No voting power')) {
                ElMessage.error({
                    message: '您在提案创建时没有投票权，无法参与投票！',
                    duration: 6000
                })
            } else {
                ElMessage.error('投票失败：' + (error.reason || error.message || '未知错误'))
            }
            throw error
        } finally {
            processing.value = false
        }
    }

    async function submitQueue(proposalId) {
        if (processing.value) return

        processing.value = true

        try {
            const result = await daoStore.submitQueue(proposalId)
            ElMessage.success('提案已进入公示期')
            return result
        } catch (error) {
            ElMessage.error('操作失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    async function submitExecute(proposalId) {
        if (processing.value) return

        processing.value = true

        try {
            const result = await daoStore.submitExecute(proposalId)
            ElMessage.success('提案已执行')
            return result
        } catch (error) {
            ElMessage.error('执行失败：' + error.message)
            throw error
        } finally {
            processing.value = false
        }
    }

    async function submitCancel(proposalId) {
        if (processing.value) return

        processing.value = true

        try {
            ElMessageBox.confirm(
                '确定要取消这个提案吗？',
                '提示',
                {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }
            ).then(async () => {
                const result = await daoStore.submitCancel(proposalId)
                ElMessage.success('提案已取消')
                return result
            })
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error('取消失败：' + error.message)
            }
            throw error
        } finally {
            processing.value = false
        }
    }

    async function checkHasVoted(proposalId) {
        try {
            const walletStore = useWalletStore()
            const address = walletStore.address

            if (!address) {
                return false
            }

            const res = await hasVoted(proposalId, address)
            return res.data || false
        } catch (error) {
            console.error('Failed to check has voted:', error)
            return false
        }
    }

    function getStateTag(state) {
        return ProposalStateTag[state] || 'info'
    }

    function getStateText(state) {
        return ProposalStateText[state] || '未知状态'
    }

    function formatTimestamp(timestamp) {
        if (!timestamp) return ''
        const date = new Date(timestamp * 1000)
        return date.toLocaleString('zh-CN')
    }

    function formatVotes(votes, decimals = 2) {
        if (!votes) return '0'
        return (Number(votes) / 10**6).toFixed(decimals)
    }

    function calculateVotePercentage(yesVotes, noVotes) {
        const yes = Number(yesVotes) || 0
        const no = Number(noVotes) || 0
        const total = yes + no
        if (total === 0) return { yes: 0, no: 0 }
        return {
            yes: Math.round((yes / total) * 100),
            no: Math.round((no / total) * 100)
        }
    }

    function getTimeRemaining(deadline) {
        if (!deadline) return ''
        const now = Math.floor(Date.now() / 1000)
        const diff = deadline - now

        if (diff <= 0) {
            return '已结束'
        }

        const days = Math.floor(diff / 86400)
        const hours = Math.floor((diff % 86400) / 3600)
        const minutes = Math.floor((diff % 3600) / 60)

        return `${days}天${hours}小时${minutes}分钟`
    }

    return {
        proposals,
        currentProposal,
        votingPower,
        daoStats,
        loading,
        pagination,
        processing,
        loadProposals,
        loadProposalDetail,
        submitProposal,
        submitVote,
        submitQueue,
        submitExecute,
        submitCancel,
        checkHasVoted,
        getStateTag,
        getStateText,
        formatTimestamp,
        getTimeRemaining,
        formatVotes,
        calculateVotePercentage
    }
}