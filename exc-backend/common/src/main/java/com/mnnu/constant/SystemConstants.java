package com.mnnu.constant;

import java.math.BigDecimal;

public class SystemConstants {

    /**
     * 代币精度常量
     */
    public static final int TOKEN_DECIMALS = 6;
    public static final BigDecimal DECIMAL_FACTOR = BigDecimal.TEN.pow(TOKEN_DECIMALS);


    public static class TradeConstants {
        /**
         * 最小交易 UT
         */
        public static final long MIN_TRADE_UT = 1;
        /**
         * 最大交易 UT
         */
        public static final long MAX_TRADE_UT = 70;
        /**
         * 交易超时（小时）
         */
        public static final long TRADE_TIMEOUT_HOURS = 5;
        /**
         * 新用户首次率先转账次数
         */
        public static final int NEW_USER_FIRST_TRADE_COUNT = 3;

        /**
         * 种子用户晋升金额（900UT）
         */
        public static final long SEED_USER_EXTH_AMOUNT = 900;
        /**
         * 手续费率（1/10000）
         */
        public static final int FEE_RATE = 1;
        /**
         * 手续费分母
         */
        public static final int FEE_DENOMINATOR = 10000;
        /**
         * 初始奖励
         */
        public static final BigDecimal INITIAL_REWARD = BigDecimal.valueOf(0.05);
        /**
         * 奖励减半间隔（UT）
         */
        public static final long REWARD_HALVING_INTERVAL = 1000_000_000L;
    }

    public static class UserType {
        public static final int NEW = 0;            //新用户
        public static final int NORMAL = 1;         //普通用户
        public static final int SEED = 2;           //种子用户
    }


    /**
     * 交易状态 (需与合约 state 字段逻辑对齐)
     */
    public static class TradeStatus {
        public static final int WAITING_MATCH = 0;      // 等待匹配
        public static final int MATCHED = 1;            // 匹配成功 (链上 Created)
        public static final int PARTY_A_CONFIRMED = 2;  // 甲方已确认 (链上 State 1)
        public static final int PARTY_B_CONFIRMED = 3;  // 乙方已确认 (链上 State 2)
        public static final int COMPLETED = 4;          // 交易成功 (链上 State 3)
        public static final int DISPUTED = 5;           // 争议中 (链上 State 5)
        public static final int RESOLVED = 6;           // 争议已解决 (链上 State 6)
        public static final int CANCELLED = 7;          // 交易取消(链上 State 4)
        public static final int EXPIRED = 8;          // 交易超时 (链上 State 7)
        public static final int PENDING_CHAIN_COMPLETE = 9;          // 等待链上确认

    }
    /**
     * 争议处理状态
     */
    public static class DisputeStatus {
        public static final int PENDING_CHAIN_COMPLETE = 0;       // 等待链上确认
        public static final int PENDING = 1;        // 处理中
        public static final int RESOLVED = 2;       // 已解决
        public static final int REJECTED = 3;       // 已驳回

    }

    public static class RedisKey {
        /**
         * 用户余额
         */
        public static final String USER_BALANCE = "user:balance:";
        /** 分布式锁前缀 */
        public static final String LOCK_PREFIX = "lock:";    }

    public static class MQQueue {
        /**
         * 交易匹配队列
         */
        public static final String TRADE_MATCH = "queue.trade.match";
        /**
         * 区块链事件队列
         */
        public static final String BLOCKCHAIN_EVENT = "queue.blockchain.event";
            }
}
