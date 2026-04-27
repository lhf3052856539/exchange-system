package com.mnnu.component.task;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.TradeMatchDTO;
import com.mnnu.entity.DisputeRecordEntity;
import com.mnnu.entity.ProposalRecordEntity;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.mapper.DisputeRecordMapper;
import com.mnnu.mapper.ProposalRecordMapper;
import com.mnnu.mapper.TradeMapper;
import com.mnnu.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务
 */
@Slf4j
@Component
public class ScheduledJobs {

    @Autowired
    private MatchingEngineService matchingEngine;
    @Autowired
    private RateService rateService;
    @Autowired
    private BlockchainService blockchainService;
    @Autowired
    private DisputeRecordMapper disputeRecordMapper;
    @Autowired
    private MultiSigWalletService multiSigWalletService;
    @Autowired
    private TradeMapper tradeMapper;
    @Autowired
    private ProposalRecordMapper proposalRecordMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 每10秒执行一次匹配
     */
    @Scheduled(fixedDelay = 10000)
    public void executeMatching() {
        log.info("[定时任务] 开始执行匹配引擎...");
        try {
            List<TradeMatchDTO> matches = matchingEngine.executeMatching();

            if (!matches.isEmpty()) {
                log.info("[定时任务] 找到 {} 个匹配，开始创建交易对", matches.size());

                log.info("[定时任务] 找到 {} 个匹配，发送到消息队列", matches.size());

                // 通过 MQ 发送匹配事件，由 TradeMatchListener 只发送通知
                String message = objectMapper.writeValueAsString(matches);
                rabbitTemplate.convertAndSend(SystemConstants.MQQueue.TRADE_MATCH, message);


            } else {
                log.debug("️ [定时任务] 本次未找到匹配");
            }
        } catch (Exception e) {
            log.error(" [定时任务] 匹配引擎执行异常: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void syncBlockchainEvents() {
        try {
            Long lastTimestamp = blockchainService.getLastSyncTimestamp();
            long newTimestamp = blockchainService.syncAllEvents(lastTimestamp);

            if (newTimestamp > lastTimestamp) {
                blockchainService.updateLastSyncTimestamp(newTimestamp);
                log.info("Blockchain events synced, updated timestamp to: {}", newTimestamp);
            } else {
                log.debug("No new events found");
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                log.warn("GraphQL rate limited, skipping this sync cycle");
            } else {
                log.error("Error syncing blockchain events", e);
            }
        }
    }


    /**
     * 每300分钟更新汇率
     */
    @Scheduled(fixedDelay = 18000000)
    public void updateRates() {
        log.info("Updating exchange rates...");
        try {
            rateService.updateRates();
        } catch (Exception e) {
            log.error("Update rates error: {}", e.getMessage(), e);
        }
    }

    /**
     * 每小时执行一次链上链下数据对账
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void reconcileBlockchainData() {
        log.info("Starting blockchain reconciliation...");
        try {
            blockchainService.reconcilePendingTrades();
        } catch (Exception e) {
            log.error("Reconciliation error: {}", e.getMessage(), e);
        }
    }

    /**
     * 每20分钟检查一次过期的交易请求
     * 逻辑：如果当前时间 > tradeRequestTime + validTime，且状态仍为 PENDING，则取消。
     */
    @Scheduled(fixedDelay = 1200000)
    public void checkExpiredTradeRequests() {
        log.info("Checking for expired trade requests...");
        try {
            List<TradeRecordEntity> pendingTrades = tradeMapper.selectList(
                    new LambdaQueryWrapper<TradeRecordEntity>()
                            .eq(TradeRecordEntity::getStatus, 0) // 假设 0 是 PENDING
                            .lt(TradeRecordEntity::getExpireTime, LocalDateTime.now())
            );

            for (TradeRecordEntity trade : pendingTrades) {
                try {
                    // 更新数据库状态为 CANCELLED/EXPIRED
                    trade.setStatus(4); // 假设 4 是 EXPIRED
                    tradeMapper.updateById(trade);

                    log.info("Trade {} has expired and been cancelled.", trade.getTradeId());
                } catch (Exception e) {
                    log.error("Failed to process expired trade {}", trade.getTradeId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error checking expired trades", e);
        }
    }

    /**
     * 每30分钟检查一次过期的 DAO 提案
     * 逻辑：投票结束后，如果票数达标，应调用 queue；如果未达标，标记为失败。
     */
    @Scheduled(fixedDelay = 1800000)
    public void checkExpiredDaoProposals() {
        log.info("Checking for expired DAO proposals...");
        try {
            List<ProposalRecordEntity> activeProposals = proposalRecordMapper.selectList(
                    new LambdaQueryWrapper<ProposalRecordEntity>()
                            .in(ProposalRecordEntity::getStatus, 0, 1) // PENDING or ACTIVE
                            .lt(ProposalRecordEntity::getDeadline, System.currentTimeMillis() / 1000)
            );

            for (ProposalRecordEntity proposal : activeProposals) {
                try {
                    // 检查链上状态并同步
                    //proposalService.syncProposalStatus(proposal.getProposalId());
                    log.info("Processing expired DAO proposal: {}", proposal.getProposalId());
                } catch (Exception e) {
                    log.error("Failed to process DAO proposal {}", proposal.getProposalId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error checking expired DAO proposals", e);
        }
    }

    /**
     * 每30分钟检查一次过期的仲裁提案（争议处理）
     * 逻辑：deadline 过后，调用 finalizeProposal 执行赔偿或驳回。
     */
    @Scheduled(fixedDelay = 1800000)
    public void syncExpiredArbitrationProposals() {
        log.info("Checking for expired arbitration proposals...");
        List<DisputeRecordEntity> expiredProposals = disputeRecordMapper.selectList(
                new LambdaQueryWrapper<DisputeRecordEntity>()
                        .eq(DisputeRecordEntity::getProposalStatus, 0) // PENDING
                        .lt(DisputeRecordEntity::getDeadline, LocalDateTime.now())
        );

        for (DisputeRecordEntity p : expiredProposals) {
            try {
                // 调用链上 finalizeProposal
                multiSigWalletService.finalizeProposal(new BigInteger(p.getProposalId()));
                log.info("Finalized arbitration proposal: {}", p.getProposalId());
            } catch (Exception e) {
                log.error("Failed to finalize proposal {}", p.getProposalId(), e);
            }
        }
    }

}
