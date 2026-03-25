// src/api/dao.js
import request from '@/utils/request'
import web3Util from '@/utils/web3'

/**
 * 获取 DAO 合约实例
 */
export function getDaoContract() {
    // 需要从配置文件读取 DAO 合约地址
    const daoAddress = import.meta.env.VITE_DAO_CONTRACT_ADDRESS
    // 这里返回合约实例，实际需要通过 ethers.js 加载 ABI
    return null
}

/**
 * 获取提案列表
 * @param {number} page - 页码
 * @param {number} size - 每页数量
 */
export function getProposals(page = 1, size = 10) {
    return request({
        url: '/dao/proposal/list',
        method: 'get',
        params: { pageNum: page, pageSize: size }
    })
}

/**
 * 获取提案详情
 * @param {number} proposalId - 提案 ID
 */
export function getProposalDetail(proposalId, address) {
    return request({
        url: '/dao/proposal/detail',
        method: 'get',
        params: { proposalId, address }
    })
}

/**
 * 创建提案（链下提交，后续触发链上操作）
 * @param {string} address - 用户地址
 * @param {object} data - 提案数据
 */
export function createProposal(address, data) {
    // 转换数据格式
    const formattedData = {
        description: data.description,
        targetContract: data.targetContract,
        value: data.value.toString(), // 转为字符串
        callData: data.callData || '0x' // 确保有默认值
    }

    return request({
        url: '/dao/proposal/create',
        method: 'post',
        params: { address },
        data: formattedData
    })
}

/**
 * 对提案投票
 * @param {string} address - 用户地址
 * @param {number} proposalId - 提案 ID
 * @param {boolean} support - 是否支持
 */
export function voteProposal(address, proposalId, support) {
    return request({
        url: '/dao/proposal/vote',
        method: 'post',
        params: { address, proposalId, support }
    })
}

/**
 * 将提案加入公示期队列
 * @param {string} address - 用户地址
 * @param {number} proposalId - 提案 ID
 */
export function queueProposal(address, proposalId) {
    return request({
        url: '/dao/proposal/queue',
        method: 'post',
        params: { proposalId }
    })
}

/**
 * 执行提案
 * @param {string} address - 用户地址
 * @param {number} proposalId - 提案 ID
 * @param {number} eta - 公示期执行时间
 */
export function executeProposal(address, proposalId, eta) {
    return request({
        url: '/dao/proposal/execute',
        method: 'post',
        params: { proposalId, eta }
    })
}

/**
 * 取消提案
 * @param {string} address - 用户地址
 * @param {number} proposalId - 提案 ID
 */
export function cancelProposal(address, proposalId) {
    return request({
        url: '/dao/proposal/cancel',
        method: 'post',
        params: { proposalId }
    })
}

/**
 * 获取用户的投票权（基于 EXTH 余额）
 * @param {string} address - 用户地址
 * @param {number} blockNumber - 区块号（可选）
 */
export function getVotingPower(address, blockNumber = null) {
    return request({
        url: '/dao/voting-power',
        method: 'get',
        params: { address, blockNumber }
    })
}

/**
 * 检查用户是否已投票
 * @param {number} proposalId - 提案 ID
 * @param {string} address - 用户地址
 */
export function hasVoted(proposalId, address) {
    return request({
        url: '/dao/proposal/has-voted',
        method: 'get',
        params: { proposalId, voter: address }
    })
}

/**
 * 获取 DAO 统计信息
 */
export function getDaoStats() {
    return request({
        url: '/dao/stats',
        method: 'get'
    })
}

/**
 * 获取投票周期
 */
export function getVotingPeriod() {
    return request({
        url: '/dao/voting-period',
        method: 'get'
    })
}

/**
 * 设置投票周期
 * @param {bigint} newPeriod - 新的投票周期
 */
export function setVotingPeriod(newPeriod) {
    return request({
        url: '/dao/voting-period/set',
        method: 'post',
        params: { newPeriod }
    })
}

/**
 * 获取提案数量
 */
export function getProposalCount() {
    return request({
        url: '/dao/proposal-count',
        method: 'get'
    })
}

/**
 * 获取提案状态
 * @param {number} proposalId - 提案 ID
 */
export function getProposalState(proposalId) {
    return request({
        url: '/dao/proposal/state',
        method: 'get',
        params: { proposalId }
    })
}
