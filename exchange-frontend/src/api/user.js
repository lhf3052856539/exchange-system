// exchange-frontend/src/api/user.js
import request from '@/utils/request'

// 更新用户类型
export function updateUserType() {
    return request({
        url: '/user/update-type',
        method: 'post'
    })
}

// 获取用户信息
export function getUserInfo() {
    return request({
        url: '/user/info',
        method: 'get'
        // 不需要传递 address 参数，因为@CurrentUser 会从 token 中提取地址
    })
}

// 用户注册
export function register(address) {
    return request({
        url: '/user/register',
        method: 'post',
        params: { address }
    })
}

// 登录
export function login(address, signature) {
    return request({
        url: '/user/login',
        method: 'post',
        params: { address, signature }
    })
}

// 获取交易统计信息
export function getTradeStats() {
    return request({
        url: '/user/trade-stats',
        method: 'get'
        // 不需要传递 address 参数，因为@CurrentUser 会从 token 中提取地址
    })
}
