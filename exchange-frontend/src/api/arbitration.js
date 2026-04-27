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
export async function createProposal(data) {
    try {
        const { ethers } = await import('ethers');

        const { useWalletStore } = await import('@/stores/modules/wallet');
        const walletStore = useWalletStore();

        if (!walletStore || !walletStore.address) {
            throw new Error('请先连接钱包');
        }

        // 1. 先调用后端API校验
        console.log('🔍 步骤1：调用后端校验...');
        await request({
            url: '/arbitration/proposal/create',
            method: 'post',
            data
        });
        console.log('✅ 后端校验通过');

        // 2. 前端调用链上合约
        console.log('🔍 步骤2：调用链上合约创建提案...');

        const contractAddress = import.meta.env.VITE_MULTI_SIG_WALLET_ADDRESS;

        if (!contractAddress) {
            throw new Error('合约地址未配置，请检查环境变量');
        }

        console.log('🔗 MultiSigWallet合约地址:', contractAddress);

        const contractABI = [
            'function createProposal(uint256 tradeId, address accused, address victim, uint256 amount, string calldata reason) external returns (uint256)',
            'function isCommitteeMember(address) view returns (bool)'
        ];

        const provider = new ethers.BrowserProvider(window.ethereum);
        const signer = await provider.getSigner();
        const userAddress = await signer.getAddress();

        const contract = new ethers.Contract(contractAddress, contractABI, signer);

        // 检查是否是委员会成员
        const isMember = await contract.isCommitteeMember(userAddress);
        if (!isMember) {
            throw new Error('只有委员会成员才能创建提案');
        }

        // 调用链上合约（允许0地址用于测试）
        console.log('📤 链上参数:', {
            chainTradeId: data.chainTradeId,
            accused: data.accusedParty,
            victim: data.victimParty,
            amount: ethers.parseUnits(data.compensationAmount.toString(), 6),
            reason: data.reason
        });

        const tx = await contract.createProposal(
            data.chainTradeId,
            data.accusedParty,
            data.victimParty,
            ethers.parseUnits(data.compensationAmount.toString(), 6),
            data.reason
        );

        console.log('📝 交易已提交:', tx.hash);

        const receipt = await tx.wait();
        console.log('✅ 交易已确认:', receipt.transactionHash);

        return receipt.hash;
    } catch (error) {
        console.error('❌ 创建提案失败:', error);
        throw error;
    }
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
            'function getProposalDetails(uint256 _proposalId) external view returns (uint256 tradeId, address accusedParty, address victimParty, uint256 compensationAmount, string reason, uint256 voteCount, uint256 rejectCount, uint8 status, uint256 createdAt, uint256 deadline)',
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

        const isMember = await contract.isCommitteeMember(userAddress);
        console.log('✅ 是否是委员会成员:', isMember);

        if (!isMember) {
            throw new Error('只有委员会成员才能投票');
        }

        // 从后端数据库获取提案详情（避免链上查询超时）
        const response = await request({
            url: `/arbitration/proposal/${proposalId}`,
            method: 'get'
        });

        const proposal = response.data;
        const statusNum = Number(proposal.status);

        console.log('📄 提案详情:', {
            tradeId: proposal.tradeId,
            accusedParty: proposal.accusedParty,
            victimParty: proposal.victimParty,
            compensationAmount: proposal.compensationAmount.toString(),
            voteCount: proposal.voteCount,
            rejectCount: proposal.rejectCount,
            status: statusNum,
            statusRaw: proposal.status,
            deadline: new Date(Number(proposal.deadline) * 1000).toLocaleString('zh-CN')
        });

        if (statusNum === 3) {
            throw new Error('❌ 提案已执行，无法投票');
        }
        if (statusNum === 2) {
            throw new Error('❌ 提案已拒绝，无法投票');
        }
        if (statusNum === 4) {
            throw new Error('❌ 提案已过期，无法投票');
        }
        if (statusNum !== 0 && statusNum !== 1) {
            throw new Error(`❌ 提案状态异常 (status=${statusNum})，无法投票`);
        }

        const now = Math.floor(Date.now() / 1000);
        const deadline = Number(proposal.deadline);
        const timeRemaining = deadline - now;

        console.log('⏰ 时间信息:', {
            currentTime: now,
            deadline: deadline,
            timeRemaining: `${timeRemaining}秒 (${Math.floor(timeRemaining / 60)}分钟)`,
            isExpired: timeRemaining <= 0
        });

        /*if (timeRemaining <= 0) {
            throw new Error('⏰ 投票期已结束');
        }*/

        console.log('⛽ 尝试估算 Gas...');
        try {
            const gasEstimate = await contract.voteProposal.estimateGas(proposalId, support);
            console.log('✅ Gas 估算成功:', gasEstimate.toString());
        } catch (gasError) {
            console.error('❌ Gas 估算失败:', gasError);
            const errorReason = gasError.reason || gasError.message;
            console.error('错误详情:', errorReason);

            if (errorReason.includes('Already voted')) {
                throw new Error('❌ 您已经投过票了！每个委员会成员只能投一次。');
            }
            if (errorReason.includes('Not in dispute')) {
                throw new Error('❌ 交易不在争议状态，可能已被解决');
            }
            if (errorReason.includes('Proposal already resolved')) {
                throw new Error('❌ 提案已解决（可能刚被其他委员执行）');
            }

            throw gasError;
        }

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
        } else if (error.message && error.message.includes('Not in dispute')) {
            errorMessage = '❌ 交易不在争议状态';
        } else if (error.message && error.message.includes('Voting period ended')) {
            errorMessage = '❌ 投票期已结束';
        } else if (error.message && error.message.includes('execution reverted')) {
            errorMessage = '❌ 交易执行失败\n\n' +
                '可能原因：\n' +
                '1. 您已经投过票了\n' +
                '2. 提案已被其他委员执行\n' +
                '3. 交易状态已变更\n\n' +
                '请刷新页面查看最新状态';
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
