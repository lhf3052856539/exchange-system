// src/api/airdrop.js
import request from '@/utils/request'

/**
 * 领取空投
 * @param {string} address - 钱包地址（从 token 获取）
 * @param {string|number} amount - 领取数量
 * @param {string[]} merkleProof - 默克尔证明（十六进制字符串数组）
 */
export function claimAirdrop(address, amount, merkleProof) {
    console.log('📤 Claim airdrop request:', {
        address,
        amount,
        merkleProof,
        proofLength: merkleProof?.length,
        firstProof: merkleProof?.[0]
    })

    return request({
        url: '/airdrop/claim',
        method: 'post',
        headers: {
            'Content-Type': 'application/json'
        },
        data: {
            amount: parseInt(amount), // 转换为整数
            merkleProof
        }
    })
}

/**
 * 检查是否已领取空投
 * @param {string} address - 钱包地址
 */
export function hasClaimed(address) {
    return request({
        url: '/airdrop/has-claimed',
        method: 'get',
        params: { address }
    })
}

/**
 * 获取空投信息
 * @param {string} address - 钱包地址
 */
export function getAirdropInfo(address) {
    return request({
        url: '/airdrop/info',
        method: 'get',
        params: { address }
    })
}
