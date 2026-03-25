package com.mnnu.component.task;
import com.mnnu.service.MatchingEngineService;
import com.mnnu.service.RateService;
import com.mnnu.service.RewardService;
import com.mnnu.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final MatchingEngineService matchingEngine;
    private final TradeService tradeService;
    private final RateService rateService;
    private final RewardService rewardService;

    /**
     * 每10秒执行一次匹配
     */
    @Scheduled(fixedDelay = 10000)
    public void executeMatching() {
        log.debug("Starting matching engine...");
        try {
            matchingEngine.executeMatching();
        } catch (Exception e) {
            log.error("Matching engine error: {}", e.getMessage(), e);
        }
    }

    /**
     * 每分钟检查过期交易
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkExpiredTrades() {
        log.debug("Checking expired trades...");
        try {
            tradeService.checkExpiredTrades();
        } catch (Exception e) {
            log.error("Check expired trades error: {}", e.getMessage(), e);
        }
    }

    /**
     * 每5分钟更新汇率
     */
    @Scheduled(fixedDelay = 300000)
    public void updateRates() {
        log.info("Updating exchange rates...");
        try {
            rateService.updateRates();
        } catch (Exception e) {
            log.error("Update rates error: {}", e.getMessage(), e);
        }
    }
    /**
     * 每年1月1日重置奖励减半计数器
     */
    @Scheduled(cron = "0 0 0 1 1 ?")
    public void resetRewardHalvingCounter() {
        log.info("=== Resetting reward halving counter for new year ===");
        try {
            rewardService.resetYearlyCounter();
            log.info("Reward halving counter reset successfully");
        } catch (Exception e) {
            log.error("Failed to reset reward halving counter", e);
        }
    }
}
