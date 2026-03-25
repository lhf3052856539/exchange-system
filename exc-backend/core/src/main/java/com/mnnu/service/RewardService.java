package com.mnnu.service;
/**
 * 奖励计算服务
 */

import java.math.BigDecimal;

public interface RewardService {
    /**
     * 重置年度计数器（每年调用一次）
     */
    void resetYearlyCounter();

    /**
     * 获取当前奖励数量
     */
    BigDecimal getCurrentReward();

    /**
     * 计算减半后的奖励
     */
    BigDecimal calculateHalvedReward(Long totalVolume);

    /**
     * 检查是否需要减半
     */
    boolean checkHalvingNeeded(Long totalVolume);

    /**
     * 发放奖励
     */
    void distributeReward(String address, BigDecimal amount, String tradeId);

    /**
     * 获取总交易量
     */
    Long getTotalUTVolume();
}