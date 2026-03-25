// src/utils/format.js
import dayjs from 'dayjs'

export function formatAddress(address, start = 6, end = 4) {
  if (!address) return ''
  return `${address.slice(0, start)}...${address.slice(-end)}`
}

export function formatNumber(num, decimals = 2) {
  if (!num && num !== 0) return '0'
  return Number(num).toFixed(decimals)
}

export function formatTime(time, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!time) return ''
  return dayjs(time).format(format)
}

export function formatTimestamp(timestamp) {
  if (!timestamp) return ''
  return dayjs.unix(timestamp).format('YYYY-MM-DD HH:mm:ss')
}

export function formatAmount(amount, unit = '') {
  const formatted = formatNumber(amount, 4)
  return unit ? `${formatted} ${unit}` : formatted
}

export function shortenString(str, length = 10) {
  if (!str || str.length <= length) return str
  return `${str.slice(0, length)}...`
}

/**
 * 格式化百分比
 */
export function formatPercentage(value, decimals = 2) {
  if (value === null || value === undefined) return '0%'
  return `${((Number(value) || 0) * 100).toFixed(decimals)}%`
}

/**
 * 格式化时间戳为相对时间（如：5 分钟前）
 */
export function formatRelativeTime(timestamp) {
  if (!timestamp) return ''

  const now = Date.now()
  const diff = now - timestamp

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}天前`
  if (hours > 0) return `${hours}小时前`
  if (minutes > 0) return `${minutes}分钟前`
  return '刚刚'
}

/**
 * 格式化字节数为可读大小
 */
export function formatBytes(bytes, decimals = 2) {
  if (bytes === 0) return '0 Bytes'

  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i]
}

/**
 * 将链上值转换为人类可读金额（除以精度）
 * @param {number|string|BigInteger} chainValue 链上值
 * @param {number} decimals 精度，默认 6
 * @returns {string} 格式化后的金额
 */
export function fromChainUnit(chainValue, decimals = 6) {
  if (!chainValue && chainValue !== 0) return '0'

  const divisor = Math.pow(10, decimals)
  const readable = Number(chainValue) / divisor
  return formatNumber(readable, decimals)
}

/**
 * 将人类可读金额转换为链上值（乘以精度）
 * @param {number|string} humanValue 人类可读金额
 * @param {number} decimals 精度，默认 6
 * @returns {string} 链上值（字符串格式，保留完整精度）
 */
export function toChainUnit(humanValue, decimals = 6) {
  if (!humanValue && humanValue !== 0) return '0'

  const multiplier = Math.pow(10, decimals)
  const chainValue = Number(humanValue) * multiplier
  return chainValue.toString()
}
