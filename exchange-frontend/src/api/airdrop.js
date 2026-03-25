// src/api/airdrop.js
import request from '@/utils/request'

/**
 * 领取空投
 * @param {string} address - 钱包地址
 */
export function claimAirdrop(address) {
    return request({
        url: '/airdrop/claim',
        method: 'post',
        params: { address }
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
