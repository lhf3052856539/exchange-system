// src/config/constants.js

// 交易状态枚举
export const TRADE_STATUS = {
  PENDING_CHAIN_CONFIRM: 'PENDING_CHAIN_CONFIRM', // 等待链上确认
  PENDING: 'PENDING',           // 待匹配
  MATCHED: 'MATCHED',           // 交易已创建，等待率先转账方确认转账
  PARTY_A_CONFIRMED: 'PARTY_A_CONFIRMED', // 等待履约方确认转账
  PARTY_B_CONFIRMED: 'PARTY_B_CONFIRMED', // 等待率先转账方最终确认
  COMPLETED: 'COMPLETED',       // 交易已完成
  FAILED: 'FAILED',             // 交易争议已解决
  CANCELLED: 'CANCELLED',       // 交易已取消
  DISPUTED: 'DISPUTED',          // 交易存在争议
  EXPIRED: 'EXPIRED'            //交易已过期
}

// 交易状态数字映射（后端返回的数字状态码）
export const TRADE_STATUS_CODE = {
  MATCHED: 0,            // 交易已创建，等待率先转账方确认转账
  PARTY_A_CONFIRMED: 1,       // 等待履约方确认转账
  PARTY_B_CONFIRMED: 2,       // 等待率先转账方最终确认
  COMPLETED: 3,          // 交易已完成
  CANCELLED: 4,          // 交易已取消
  DISPUTED: 5,           // 交易存在争议
  FAILED: 6,              //交易争议已解决
  EXPIRED: 7,            // 交易已过期
  PENDING_CHAIN_CONFIRM: 8, // 待链上确认
  WAITING_MATCH: 9      // 等待匹配

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