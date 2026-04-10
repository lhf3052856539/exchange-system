package com.mnnu.component.task;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
@RequiredArgsConstructor
public class ScheduledJobs {

    private final MatchingEngineService matchingEngine;
    private final TradeService tradeService;
    private final RateService rateService;
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

    /**
     * 每10秒执行一次匹配
     */
    @Scheduled(fixedDelay = 10000)
    public void executeMatching() {
        try {
            List<TradeMatchDTO> matches = matchingEngine.executeMatching();

            if (!matches.isEmpty()) {
                log.info("Found {} matches, creating trade pairs", matches.size());

                for (TradeMatchDTO match : matches) {
                    try {
                        tradeService.createTradePair(match);
                    } catch (Exception e) {
                        log.error("Failed to create trade pair for match: {}", match, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Matching engine error: {}", e.getMessage(), e);
        }
    }


    /**
     * 每30分钟更新汇率
     */
    @Scheduled(fixedDelay = 1800000)
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
     * 每10分钟检查一次过期的交易请求
     * 逻辑：如果当前时间 > tradeRequestTime + validTime，且状态仍为 PENDING，则取消。
     */
    @Scheduled(fixedDelay = 600000)
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
     * 每15分钟检查一次过期的仲裁提案（争议处理）
     * 逻辑：deadline 过后，调用 finalizeProposal 执行赔偿或驳回。
     */
    @Scheduled(fixedDelay = 900000)
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
                multiSigWalletService.finalizeProposal(new java.math.BigInteger(p.getProposalId()));
                log.info("Finalized arbitration proposal: {}", p.getProposalId());
            } catch (Exception e) {
                log.error("Failed to finalize proposal {}", p.getProposalId(), e);
            }
        }
    }

}
