// src/api/rate.js
import request from '@/utils/request'

export function getRates() {
    return request({
        url: '/rate/all',
        method: 'get'
    })
}

export function getRatePair(from, to) {
    return request({
        url: '/rate/pair',
        method: 'get',
        params: { from, to }
    })
}
