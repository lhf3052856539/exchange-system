// src/api/arbitration.js
import request from '@/utils/request'

/**
 * 获取仲裁委员会成员列表
 */
export function getCommitteeMembers() {
    return request({
        url: '/arbitration/committee',
        method: 'get'
    })
}

/**
 * 检查当前用户是否为委员会成员
 */
export function checkCommitteeMember() {
    return request({
        url: '/arbitration/check-committee',
        method: 'get'
    })
}

/**
 * 获取待处理的仲裁提案
 */
export function getPendingProposals() {
    return request({
        url: '/arbitration/proposal/pending',
        method: 'get'
    })
}

/**
 * 获取仲裁历史
 */
export function getHistoryProposals() {
    return request({
        url: '/arbitration/proposal/history',
        method: 'get'
    })
}

/**
 * 获取提案详情
 */
export function getProposalDetail(proposalId) {
    return request({
        url: `/arbitration/proposal/${proposalId}`,
        method: 'get'
    })
}

/**
 * 创建仲裁提案
 */
export function createProposal(data) {
    return request({
        url: '/arbitration/proposal/create',
        method: 'post',
        data
    })
}

/**
 * 对仲裁提案投票（使用前端钱包签名）
 */
export async function voteProposal(proposalId, support) {
    try {
        const { ethers } = await import('ethers');

        const contractAddress = import.meta.env.VITE_MULTI_SIG_WALLET_ADDRESS;

        if (!contractAddress) {
            throw new Error('合约地址未配置，请检查环境变量');
        }

        console.log('🔍 合约地址:', contractAddress);

        const contractABI = [
            'function voteProposal(uint256 _proposalId, bool _support) external',
            'function proposals(uint256) view returns (uint256 tradeId, address accusedParty, address victimParty, uint256 compensationAmount, string reason, uint256 voteCount, bool executed, bool rejected, uint256 createdAt, uint256 deadline)',
            'function isCommitteeMember(address) view returns (bool)'
        ];

        const provider = new ethers.BrowserProvider(window.ethereum);
        const signer = await provider.getSigner();
        const userAddress = await signer.getAddress();

        console.log('📋 投票信息:', {
            proposalId: proposalId.toString(),
            support: support,
            userAddress: userAddress,
            contractAddress: contractAddress
        });

        const contract = new ethers.Contract(contractAddress, contractABI, signer);

        // 检查是否是委员会成员
        const isMember = await contract.isCommitteeMember(userAddress);
        console.log('✅ 是否是委员会成员:', isMember);

        if (!isMember) {
            throw new Error('只有委员会成员才能投票');
        }

        // 获取提案详情
        const proposal = await contract.proposals(proposalId);
        console.log('📄 提案详情:', {
            tradeId: proposal.tradeId.toString(),
            accusedParty: proposal.accusedParty,
            victimParty: proposal.victimParty,
            compensationAmount: proposal.compensationAmount.toString(),
            voteCount: proposal.voteCount.toString(),
            executed: proposal.executed,
            rejected: proposal.rejected,
            deadline: new Date(Number(proposal.deadline) * 1000).toLocaleString('zh-CN')
        });

        // 检查时间
        const now = Math.floor(Date.now() / 1000);
        const deadline = Number(proposal.deadline);
        const timeRemaining = deadline - now;

        console.log('⏰ 时间信息:', {
            currentTime: now,
            deadline: deadline,
            timeRemaining: timeRemaining,
            isExpired: timeRemaining <= 0
        });

        if (timeRemaining <= 0) {
            throw new Error('⏰ 投票期已结束');
        }

        if (proposal.executed) {
            throw new Error('❌ 提案已执行，无法投票');
        }

        if (proposal.rejected) {
            throw new Error('❌ 提案已被拒绝，无法投票');
        }

        // 尝试估算 Gas（这里会失败，但能看到具体错误）
        console.log('⛽ 尝试估算 Gas...');
        try {
            const gasEstimate = await contract.voteProposal.estimateGas(proposalId, support);
            console.log('✅ Gas 估算成功:', gasEstimate.toString());
        } catch (gasError) {
            console.error('❌ Gas 估算失败:', gasError);
            console.error('错误详情:', gasError.reason || gasError.message);

            // 从错误中提取 revert reason
            if (gasError.message && gasError.message.includes('Already voted')) {
                throw new Error('❌ 您已经投过票了！每个委员会成员只能投一次。\n\n' +
                    '当前钱包地址：' + userAddress + '\n' +
                    '解决方案：\n' +
                    '1. 更换另一个委员会成员的钱包\n' +
                    '2. 或者创建一个新的提案');
            }
        }

        // 发送交易
        console.log('🚀 准备发送交易...');
        const tx = await contract.voteProposal(proposalId, support);
        console.log('📝 交易已提交:', tx.hash);

        console.log('⏳ 等待交易确认...');
        const receipt = await tx.wait();
        console.log('✅ 交易已确认:', receipt.transactionHash);

        return receipt.hash;
    } catch (error) {
        console.error('❌ 投票失败:', error);

        let errorMessage = error.message || '投票失败';

        if (error.message && error.message.includes('Already voted')) {
            errorMessage = error.message;
        } else if (error.message && error.message.includes('Not a committee member')) {
            errorMessage = '❌ 只有委员会成员才能投票';
        } else if (error.message && error.message.includes('Invalid proposal ID')) {
            errorMessage = '❌ 提案 ID 不存在';
        } else if (error.message && error.message.includes('Proposal already resolved')) {
            errorMessage = '❌ 提案已解决（已执行或被拒绝）';
        } else if (error.message && error.message.includes('Voting period ended')) {
            errorMessage = '❌ 投票期已结束';
        } else if (error.message && error.message.includes('execution reverted')) {
            errorMessage = '❌ 交易执行失败\n\n' +
                '可能原因：\n' +
                '1. 您已经投过票了（最可能）\n' +
                '2. 不是委员会成员\n' +
                '3. 提案已解决\n' +
                '4. 投票期已结束\n\n' +
                '请查看控制台的详细错误信息';
        }

        throw new Error(errorMessage);
    }
}



/**
 * 执行仲裁提案
 */
export function executeProposal(proposalId) {
    return request({
        url: `/arbitration/proposal/execute?proposalId=${proposalId}`,
        method: 'post'
    })
}

/**
 * 获取待处理的争议列表
 */
export function getPendingDisputes() {
    return request({
        url: '/arbitration/dispute/pending',
        method: 'get'
    })

}
