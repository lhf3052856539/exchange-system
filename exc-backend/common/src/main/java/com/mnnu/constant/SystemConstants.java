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


    public static class TradeStatus {
        public static final int WAITING_MATCH = 0;      //等待匹配
        public static final int MATCHED = 1;            //匹配成功
        public static final int CONFIRMING_A = 2;       //等待甲方确认（提交哈希）
        public static final int PARTY_A_CONFIRMED = 3;  //甲方已确认（等待乙方确认）
        public static final int CONFIRMING_B = 4;       //等待乙方确认（提交哈希）
        public static final int PARTY_B_CONFIRMED = 5;  //乙方已确认（等待甲方最终确认）
        public static final int PENDING_CHAIN_CONFIRM = 6;  //待链上确认（已提交链上交易）
        public static final int COMPLETED = 7;          //交易成功
        public static final int DISPUTED = 8;           //交易存在争议
        public static final int EXPIRED = 9;            //交易超时
        public static final int CANCELLED = 10;         //交易取消
    }
    public static class DisputeStatus {
        public static final int PENDING = 0;        //待处理
        public static final int RESOLVED = 1;       // 争议已解决
        public static final int REJECTED = 2;       //拒绝
        public static final int BLACKLISTED = 3;    //加入黑名单
    }

    public static class RedisKey {
        /**
         * 用户信息
         */
        public static final String USER_INFO = "user:info:";
        /**
         * 用户余额
         */
        public static final String USER_BALANCE = "user:balance:";
        /**
         * 交易队列
         */
        public static final String TRADE_QUEUE = "trade:queue";
        /**
         * 交易信息
         */
        public static final String TRADE_INFO = "trade:info:";
        /**
         * 交易匹配信息
         */
        public static final String TRADE_MATCH = "trade:match:";
        /**
         * 等待匹配队列
         */
        public static final String WAITING_MATCH = "waiting:match:";
        /**
         * 待确认队列
         */
        public static final String RATE_CACHE = "rate:cache";
        /**
         * 随机数缓存
         */
        public static final String NONCE = "nonce:";
        /**
         * 空投状态
         */
        public static final String USER_AIRDROP_STATUS = "user:airdrop:status:";
        /**
         * 空投数量
         */
        public static final String USER_TOTAL_AIRDROP = "user:airdrop:total:";

    }

    public static class MQQueue {
        /**
         * 交易匹配队列
         */
        public static final String TRADE_MATCH = "queue.trade.match";
        /**
         * 交易确认队列
         */
        public static final String TRADE_CONFIRM = "queue.trade.confirm";
        /**
         * 争议处理队列
         */
        public static final String TRADE_DISPUTE = "queue.trade.dispute";
        /**
         * 区块链事件队列
         */
        public static final String BLOCKCHAIN_EVENT = "queue.blockchain.event";
        /**
         * 通知队列
         */
        public static final String NOTIFICATION = "queue.notification";
        /**
         * 空投事件队列
         */
        public static final String AIRDROP_EVENT = "queue.airdrop.event";
        /**
         * 用户事件队列
         */
        public static final String USER_EVENT = "queue.user.event";
    }

    public static class RewardConstants {
        /**
         * 默认空投奖励金额
         */
        public static final long DEFAULT_REWARD_AMOUNT = 100;
        public static final long MIN_REWARD_AMOUNT = 10;
        public static final long MAX_REWARD_AMOUNT = 1000;
        public static final double AIRDROP_REWARD_PERCENT = 0.001;
    }
}
