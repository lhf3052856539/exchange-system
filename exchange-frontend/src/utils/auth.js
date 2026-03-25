// src/utils/auth.js
import storage from './storage'

const TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refresh_token'

/**
 * 存储 token
 */
export function setToken(token) {
    storage.set(TOKEN_KEY, token)
}

/**
 * 获取 token
 */
export function getToken() {
    return storage.get(TOKEN_KEY)
}

/**
 * 删除 token
 */
export function removeToken() {
    storage.remove(TOKEN_KEY)
    storage.remove(REFRESH_TOKEN_KEY)
}

/**
 * 检查是否已登录
 */
export function isLoggedIn() {
    const token = getToken()
    return !!token
}

/**
 * 解析 JWT token（不验证签名，仅解码 payload）
 */
export function parseToken(token) {
    try {
        const base64Url = token.split('.')[1]
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
        const jsonPayload = decodeURIComponent(
            atob(base64).split('').map(c =>
                '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
            ).join('')
        )
        return JSON.parse(jsonPayload)
    } catch (error) {
        console.error('Failed to parse token:', error)
        return null
    }
}

/**
 * 检查 token 是否过期
 */
export function isTokenExpired(token) {
    const payload = parseToken(token)
    if (!payload || !payload.exp) return true

    const now = Date.now() / 1000
    return payload.exp < now
}

/**
 * 检查 token 是否即将过期（默认 5 分钟内）
 */
export function isTokenExpiringSoon(token, threshold = 300) {
    const payload = parseToken(token)
    if (!payload || !payload.exp) return true

    const now = Date.now() / 1000
    return payload.exp < (now + threshold)
}
