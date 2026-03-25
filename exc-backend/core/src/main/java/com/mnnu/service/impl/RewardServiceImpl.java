package com.mnnu.service.impl;

import com.mnnu.constant.SystemConstants;
import com.mnnu.mapper.TradeRecordMapper;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.RewardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
// 我们删除了 @RequiredArgsConstructor
public class RewardServiceImpl implements RewardService {

    private final TradeRecordMapper tradeMapper;
    private final BlockchainService blockchainService; // @Lazy 注解从这里移走了
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REWARD_COUNTER_KEY = "reward:halving:counter";

    // 这是我们手动编写的构造函数
    @Autowired
    public RewardServiceImpl(TradeRecordMapper tradeMapper,
                             @Lazy BlockchainService blockchainService, // @Lazy 注解现在在这里！
                             RedisTemplate<String, String> redisTemplate) {
        this.tradeMapper = tradeMapper;
        this.blockchainService = blockchainService;
        this.redisTemplate = redisTemplate;
    }


    /**
     * 获取当前奖励数量
     */
    @Override
    public BigDecimal getCurrentReward() {
        // 从 Redis 获取已累积的交易量
        String volumeStr = redisTemplate.opsForValue().get(REWARD_COUNTER_KEY);
        Long totalVolume = volumeStr != null ? Long.parseLong(volumeStr) : 0L;

        return calculateHalvedReward(totalVolume);
    }

    /**
     * 计算减半后的奖励
     */
    @Override
    public BigDecimal calculateHalvedReward(Long totalVolume) {
        if (totalVolume == null || totalVolume <= 0) {
            return SystemConstants.TradeConstants.INITIAL_REWARD;
        }

        long halvingTimes = totalVolume / SystemConstants.TradeConstants.REWARD_HALVING_INTERVAL;


        BigDecimal reward = SystemConstants.TradeConstants.INITIAL_REWARD;

        for (int i = 0; i < halvingTimes; i++) {
            reward = reward.multiply(new BigDecimal("0.05"));

        }

        return reward.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 检查是否需要减半
     */
    @Override
    public boolean checkHalvingNeeded(Long totalVolume) {
        if (totalVolume == null || totalVolume <= 0) {
            return false;
        }

        long currentHalvings = totalVolume / SystemConstants.TradeConstants.REWARD_HALVING_INTERVAL;
        long nextHalvingVolume = (currentHalvings + 1) * SystemConstants.TradeConstants.REWARD_HALVING_INTERVAL;
        boolean needHalving = totalVolume >= nextHalvingVolume;

        if (needHalving) {
            log.info("Reward halving triggered! Total volume: {}, Next halving at: {}",
                    totalVolume, nextHalvingVolume);
        }

        return needHalving;
    }

    /**
     * 发放奖励
     */
    @Override
    public void distributeReward(String address, BigDecimal amount, String tradeId) {
        try {
            log.info("Distributing reward: address={}, amount={}, tradeId={}", address, amount, tradeId);

            String txHash = blockchainService.distributeRewardOnChain(address, amount.toBigInteger());

            log.info("Reward distributed successfully: txHash={}", txHash);

        } catch (Exception e) {
            log.error("Failed to distribute reward for {}: {}", address, e.getMessage(), e);
            throw new RuntimeException("Reward distribution failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取总交易量
     */
    @Override
    public Long getTotalUTVolume() {
        try {
            Long totalVolume = tradeMapper.selectTotalUTVolume();
            return totalVolume != null ? totalVolume : 0L;
        } catch (Exception e) {
            log.error("Failed to get total UT volume", e);
            return 0L;
        }
    }

    /**
     * 重置年度计数器
     */
    @Override
    public void resetYearlyCounter() {
        try {
            // 清除 Redis 中的计数器
            redisTemplate.delete(REWARD_COUNTER_KEY);

            log.info("Yearly reward halving counter has been reset");
        } catch (Exception e) {
            log.error("Failed to reset yearly counter", e);
            throw new RuntimeException("Reset failed: " + e.getMessage(), e);
        }
    }
}

