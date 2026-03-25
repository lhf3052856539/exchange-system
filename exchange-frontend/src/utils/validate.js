// src/utils/validate.js

/**
 * 验证以太坊地址格式
 */
export function isValidAddress(address) {
    if (!address) return false
    return /^0x[a-fA-F0-9]{40}$/.test(address)
}

/**
 * 验证交易哈希格式
 */
export function isValidTxHash(hash) {
    if (!hash) return false
    return /^0x[a-fA-F0-9]{64}$/.test(hash)
}

/**
 * 验证是否为有效的数字
 */
export function isValidNumber(value, options = {}) {
    const { min, max, allowZero = true, allowNegative = false } = options

    if (value === '' || value === null || value === undefined) return false

    const num = Number(value)
    if (isNaN(num)) return false

    if (!allowZero && num === 0) return false
    if (!allowNegative && num < 0) return false
    if (min !== undefined && num < min) return false
    if (max !== undefined && num > max) return false

    return true
}

/**
 * 验证金额（正数，可指定精度）
 */
export function isValidAmount(amount, decimals = 6) {
    if (!amount) return false

    const regex = new RegExp(`^\\d+(\\.\\d{1,${decimals}})?$`)
    return regex.test(amount.toString()) && Number(amount) > 0
}

/**
 * 验证邮箱格式
 */
export function isValidEmail(email) {
    if (!email) return false
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

/**
 * 验证 URL 格式
 */
export function isValidUrl(url) {
    if (!url) return false
    try {
        new URL(url)
        return true
    } catch {
        return false
    }
}

/**
 * 验证字符串长度
 */
export function isValidLength(str, min, max) {
    if (!str) return false
    const length = str.length
    if (min !== undefined && length < min) return false
    if (max !== undefined && length > max) return false
    return true
}

/**
 * 验证合约地址（可以是多个地址或单个地址）
 */
export function isValidContractAddress(address) {
    if (!address) return false
    if (Array.isArray(address)) {
        return address.every(addr => isValidAddress(addr))
    }
    return isValidAddress(address)
}
