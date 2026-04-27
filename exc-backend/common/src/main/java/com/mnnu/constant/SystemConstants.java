package com.mnnu.constant;

import java.math.BigDecimal;

public class SystemConstants {

    /**
     * 代币精度常量
     */
    public static final int TOKEN_DECIMALS = 6;


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
        public static final int MATCHED = 0;            // 交易已创建，等待率先转账方确认转账
        public static final int PARTY_A_CONFIRMED = 1;  // 等待履约方确认转账
        public static final int PARTY_B_CONFIRMED = 2;  // 等待率先转账方最终确认
        public static final int COMPLETED = 3;          // 交易已完成
        public static final int CANCELLED = 4;          // 交易已取消
        public static final int DISPUTED = 5;           // 交易存在争议
        public static final int RESOLVED = 6;           // 交易争议已解决
        public static final int EXPIRED = 7;          // 交易已过期
        public static final int PENDING_CHAIN_COMPLETE = 8;          // 等待链上确认
        public static final int WAITING_PARTY_B_CREATE_PAIR = 9;     // 等待乙方创建交易对


    }
    /**
     * 争议处理状态
     */
    public static class DisputeStatus {
        public static final int NO_DISPUTE = 0;       // 无争议
        public static final int PENDING = 1;        // 争议处理中
        public static final int RESOLVED = 2;       // 争议请求已执行
        public static final int REJECTED = 3;       // 争议请求已驳回
        public static final int EXPIRED = 4;       // 争议已过期

    }

    /**
     * 仲裁提案状态
     */
    public static class ProposalStatus {
        public static final int PENDING_SUBMIT = 0;       // 仲裁提案已创建，等待投票中
        public static final int SUBMITTED = 1;            // 投票中
        public static final int REJECTED = 2;             // 仲裁提案被驳回
        public static final int PASSED = 3;               // 仲裁提案已执行
        public static final int EXECUTED = 4;             // 仲裁提案已执行
        public static final int EXPIRED = 5;              // 仲裁提案已过期

    }


    public static class RedisKey {

        // Redis Key 前缀
        public static final String EVENT_PROCESSED_KEY_PREFIX = "blockchain:event:processed:";
        // 去重记录过期时间（7天）
        public static final long EVENT_DEDUP_EXPIRE_SECONDS = 7 * 24 * 60 * 60;

        //redis游标
        public static final String LAST_SYNC_KEY = "blockchain:last_sync_timestamp";

        // 交易分布式锁 Key 前缀
        public static final String TRADE_LOCK_KEY_PREFIX = "trade:lock:";
        // 仲裁分布式锁 Key 前缀
        public static final String ARBITRATION_LOCK_KEY_PREFIX = "arbitration:lock:";

        // DAO 提案同步锁 Key 前缀
        public static final String DAO_SYNC_LOCK_KEY_PREFIX = "dao:sync:";
        public static final String AIRDROP_LOCK_KEY_PREFIX = "airdrop:lock:";
        // 仲裁委员会成员锁 Key 前缀
        public static final String COMMITTEE_LOCK_KEY_PREFIX = "committee:lock:";
        // 用户锁 Key 前缀
        public static final String USER_LOCK_KEY_PREFIX = "user:lock:";



    }

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
