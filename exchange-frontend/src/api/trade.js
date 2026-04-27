// src/api/trade.js
import request from '@/utils/request'

export function requestMatch(data) {
    return request({
        url: '/trade/request-match',
        method: 'post',
        data,
        headers: {
            'Content-Type': 'application/json'
        }
    })
}

export function getUserTrades(address, status, page = 1, size = 10) {
    return request({
        url: '/trade/list',
        method: 'get',
        params: { address, status, page, size }
    })
}

export const createTradePair = (data) => {
    return request({
        url: '/trade/create-pair',
        method: 'post',
        data
    })
}

export function confirmPartyA(address, tradeId, txHash) {
    return request({
        url: '/trade/confirm-party-a',
        method: 'post',
        params: { address, tradeId, txHash }
    })
}

export function confirmPartyB(address, tradeId, txHash) {
    return request({
        url: '/trade/confirm-party-b',
        method: 'post',
        params: { address, tradeId, txHash }
    })
}

export function disputeTrade(data) {
    return request({
        url: '/trade/dispute',
        method: 'post',
        data
    })
}

/**
 * 检查 EXTH 授权额度
 */
export function checkExthAllowance() {
    return request({
        url: '/blockchain/allowance',
        method: 'get'
    }).then(res => {
        // 直接返回整个响应，因为拦截器已经处理过 res.data
        return res
    })
}

/**
 * 授权 Exchange 合约
 */
export function approveExth(amount) {
    return request({
        url: '/blockchain/approve',
        method: 'post',
        data: { amount }
    }).then(res => {
        return res
    })
}

export function getTradeDetail(tradeId) {
    return request({
        url: `/trade/detail/${tradeId}`,
        method: 'get'
    })
}

