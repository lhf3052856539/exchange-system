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

export function getTradeDetail(tradeId) {
    return request({
        url: `/trade/detail/${tradeId}`,
        method: 'get'
    })
}

export function getUserTrades(address, status = null, page = 1, size = 10) {
    return request({
        url: '/trade/list',
        method: 'get',
        params: {
            address: String(address),
            status,
            page,
            size
        }
    })
}
