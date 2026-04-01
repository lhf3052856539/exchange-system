// src/config/constants.js

// 交易状态枚举
export const TRADE_STATUS = {
  PENDING: 'PENDING',           // 待匹配
  MATCHED: 'MATCHED',           // 已匹配
  CONFIRMING_A: 'CONFIRMING_A', // 甲方确认中（等待甲方提交转账哈希）
  PARTY_A_CONFIRMED: 'PARTY_A_CONFIRMED', // 甲方已确认（等待乙方确认）
  CONFIRMING_B: 'CONFIRMING_B', // 乙方确认中（等待乙方提交转账哈希）
  PARTY_B_CONFIRMED: 'PARTY_B_CONFIRMED', // 乙方已确认（等待甲方最终确认）
  COMPLETED: 'COMPLETED',       // 已完成
  FAILED: 'FAILED',             // 失败
  CANCELLED: 'CANCELLED',       // 已取消
  DISPUTED: 'DISPUTED'          // 争议中
}

// 交易状态数字映射（后端返回的数字状态码）
export const TRADE_STATUS_CODE = {
  WAITING_MATCH: 0,      // 等待匹配
  MATCHED: 1,            // 已匹配
  CONFIRMING_A: 2,       // 等待甲方确认
  PARTY_A_CONFIRMED: 3,  // 甲方已确认
  CONFIRMING_B: 4,       // 等待乙方确认
  PARTY_B_CONFIRMED: 5,  // 乙方已确认
  PENDING_CHAIN_CONFIRM: 6, // 待链上确认
  COMPLETED: 7,          // 已完成
  DISPUTED: 8,           // 争议中
  EXPIRED: 9,            // 已过期
  CANCELLED: 10          // 已取消
}

// 用户类型枚举
export const USER_TYPE = {
  NEW: 'NEW',       // 新用户
  NORMAL: 'NORMAL', // 普通用户
  SEED: 'SEED'      // 种子用户
}

// 币种配置
export const CURRENCY_CONFIG = {
  FROM_CURRENCIES: ['RNB', 'GBP'],
  TO_CURRENCIES: ['RNB', 'GBP']
}

// 交易限制
export const TRADE_LIMITS = {
  MIN_UT: 1,
  MAX_UT: 70
}

// 空投配置
export const AIRDROP_CONFIG = {
  FIXED_AMOUNT: 100, // 每个地址可领取的空投数量
  DECIMALS: 6        // 代币精度
}


// DAO 提案状态枚举
export const PROPOSAL_STATE = {
  PENDING: 'Pending',      // 待开始
  ACTIVE: 'Active',        // 投票中
  SUCCEEDED: 'Succeeded',  // 已通过
  FAILED: 'Failed',        // 失败
  QUEUED: 'Queued',        // 已入队列
  EXECUTED: 'Executed',    // 已执行
  CANCELLED: 'Cancelled'   // 已取消
}

// DAO治理配置
export const DAO_CONFIG = {
  VOTING_PERIOD: 3 * 24 * 60 * 60,  // 投票周期：3 天（秒）
  QUORUM_PERCENTAGE: 4,             // 法定人数比例：4%
  PROPOSAL_THRESHOLD: 100,          // 提案门槛：100 EXTH
  EXECUTION_DELAY: 7 * 24 * 60 * 60 // 执行延迟：7 天（秒）
}

// 时间常量
export const TIME_CONSTANTS = {
  SECOND: 1000,
  MINUTE: 60 * 1000,
  HOUR: 60 * 60 * 1000,
  DAY: 24 * 60 * 60 * 1000
}