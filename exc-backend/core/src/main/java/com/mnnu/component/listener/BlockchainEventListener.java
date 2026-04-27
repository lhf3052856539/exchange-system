package com.mnnu.component.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.entity.*;
import com.mnnu.mapper.*;
import com.mnnu.service.*;
import com.mnnu.wrapper.*;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import static com.mnnu.constant.SystemConstants.MQQueue.BLOCKCHAIN_EVENT;
import static com.mnnu.constant.SystemConstants.RedisKey.*;
import static com.mnnu.constant.SystemConstants.TradeConstants.TRADE_TIMEOUT_HOURS;
import static com.mnnu.utils.Web3jUtil.fromChainUnit;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 区块链事件监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainEventListener {

    /**
     * 通知服务
     */
    private final NotificationService notificationService;


    /**
     * DAO 合约包装器
     */
    @Autowired
    private DaoWrapper daoWrapper;

    /**
     * Exchange 合约包装器
     */
    @Autowired
    private ExchangeWrapper exchangeContract;
    @Autowired
    private UserMapper userMapper;
    /**
     * Redisson 客户端
     */
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private MultiSigWalletService multiSigWalletService;
    @Autowired
    private TradeMapper tradeMapper;
    @Autowired
    private DisputeRecordMapper disputeRecordMapper;
    @Autowired
    private ProposalRecordMapper proposalRecordMapper;
    @Autowired
    private DaoService daoService;
    @Autowired
    private CommitteeMemberMapper committeeMemberMapper;

    @Autowired
    private AirdropConfigMapper airdropConfigMapper;
    @Autowired
    private AirdropWhitelistMapper airdropWhitelistMapper;
    @Autowired
    private EXTHWrapper exthWrapper;
    @Autowired
    private MultiSigWalletWrapper multiSigWalletWrapper;


    /**
     * 校验事件是否已处理（基于 txHash + eventType 防重）
     */
    private boolean isEventProcessed(String txHash, String eventType) {
        if (txHash == null || txHash.isEmpty()) {
            return false;
        }

        // 生成唯一Key：blockchain:event:processed:{eventType}:{txHash}
        String dedupKey = EVENT_PROCESSED_KEY_PREFIX + eventType + ":" + txHash;

        // 使用 Redisson 的 RBucket.trySet() 实现 SETNX
        RBucket<Integer> bucket = redissonClient.getBucket(dedupKey);
        boolean wasAbsent = bucket.trySet(1, EVENT_DEDUP_EXPIRE_SECONDS, SECONDS);

        // 如果 wasAbsent 为 false，说明 key 已存在，事件已处理过
        boolean alreadyProcessed = !wasAbsent;

        if (alreadyProcessed) {
            log.warn("Event already processed: eventType={}, txHash={}", eventType, txHash);
        }

        return alreadyProcessed;
    }


    // application.yml 注入合约地址
    @Value("${contract.exth.address}")
    private String exthContractAddress;


    @RabbitListener(queues = BLOCKCHAIN_EVENT, ackMode = "MANUAL")
    public void handleBlockchainEvent(Map<String, Object> event, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (event == null || event.isEmpty()) {
            log.warn("Received empty blockchain event");
            try {
                // 空消息直接确认
                channel.basicAck(deliveryTag, false);
            } catch (Exception e) {
                log.error("Failed to ack empty message", e);
            }
            return;
        }

        try {
            String eventType = (String) event.get("event");
            String txHash = (String) event.get("txHash");

            // 先检查是否已处理（防重）
            if (isEventProcessed(txHash, eventType)) {
                log.info("Event skipped (duplicate): type={}, txHash={}", eventType, txHash);
                // 重复消息也要确认，避免无限重试
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("Received blockchain event: type={}, txHash={}, data={}", eventType, txHash, event);

            switch (eventType) {
                case "TradeCreate":
                    handleTradeCreateEvent(event);
                    break;
                case "TradeCompleted":
                    handleTradeCompletedEvent(event);
                    break;
                case "AirdropClaimed":
                    handleAirdropEvent(event);
                    break;
                case "UserBlacklisted":
                    handleUserBlacklistEvent(event);
                    break;
                case "RewardDistributed":
                    handleRewardEvent(event);
                    break;
                case "FeeCollected":
                    handleFeeCollectedEvent(event);
                    break;
                case "CompensationPaid":
                    handleCompensationPaidEvent(event);
                    break;
                // DAO 治理相关事件
                case "ProposalCreated":
                    handleDaoProposalCreatedEvent(event);
                    break;
                case "VoteCast":
                    handleDaoVoteCastEvent(event);
                    break;
                case "ProposalQueued":
                    handleDaoProposalQueuedEvent(event);
                    break;
                case "ProposalExecuted":
                    handleDaoProposalExecutedEvent(event);
                    break;
                case "ProposalCanceled":
                    handleDaoProposalCanceledEvent(event);
                    break;
                case "CommitteeMemberAdded":
                    handleCommitteeMemberAddedEvent(event);
                    break;
                case "CommitteeMemberRemoved":
                    handleCommitteeMemberRemovedEvent(event);
                    break;
                case "UserUpgraded":
                    handleUserUpgradedEvent(event);
                    break;
                case "PartyAConfirmed":
                    handlePartyAConfirmedEvent(event);
                    break;
                case "PartyBConfirmed":
                    handlePartyBConfirmedEvent(event);
                    break;
                case "TradeCancelled":
                    handleTradeCancelledEvent(event);
                    break;
                case "TradeDisputed":
                    handleTradeDisputedEvent(event);
                    break;
                case "TradeExpired":
                    handleTradeExpiredEvent(event);
                    break;
                // 多签钱包（仲裁）相关事件
                case "ArbitrationProposalCreated":
                    handleArbitrationProposalCreatedEvent(event);
                    break;
                case "ArbitrationProposalVoted":
                    handleArbitrationProposalVotedEvent(event);
                    break;
                case "ArbitrationProposalExecuted":
                    handleArbitrationProposalExecutedEvent(event);
                    break;
                case "ArbitrationProposalRejected":
                    handleArbitrationProposalRejectedEvent(event);
                    break;
                case "ArbitrationProposalExpired":
                    handleArbitrationProposalExpiredEvent(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }

            // 所有业务逻辑执行成功后，手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("Event processed successfully: type={}, txHash={}", eventType, txHash);

        } catch (Exception e) {
            log.error("Error processing blockchain event: {}", event, e);

            try {
                // 处理失败，拒绝消息并重新入队（requeue=true）
                // 这样 MQ 会重新投递该消息，触发重试
                channel.basicNack(deliveryTag, false, true);
                log.warn("Message nacked and will be requeued for retry");
            } catch (Exception nackException) {
                log.error("Failed to nack message", nackException);
            }

            // 抛出异常让 Spring AMQP 的重试机制介入
            throw new RuntimeException("Failed to process blockchain event", e);
        }
    }


    /**
     * 处理空投领取事件 (Merkle Airdrop)
     */
    private void handleAirdropEvent(Map<String, Object> event) {
        try {
            String recipient = (String) event.get("claimant");
            String amountStr = (String) event.get("amount");
            String txHash = (String) event.get("txHash");

            log.info("Processing AirdropClaimed: recipient={}, amount={}", recipient, amountStr);

            RLock lock = redissonClient.getLock(AIRDROP_LOCK_KEY_PREFIX + recipient);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Airdrop claim for {} locked, skip processing", recipient);
                    return;
                }

                LambdaQueryWrapper<AirdropConfigEntity> configWrapper = new LambdaQueryWrapper<>();
                configWrapper.eq(AirdropConfigEntity::getStatus, 1).last("LIMIT 1");
                AirdropConfigEntity config = airdropConfigMapper.selectOne(configWrapper);

                if (config != null) {
                    LambdaQueryWrapper<AirdropWhitelistEntity> whiteWrapper = new LambdaQueryWrapper<>();
                    whiteWrapper.eq(AirdropWhitelistEntity::getConfigId, config.getId())
                            .eq(AirdropWhitelistEntity::getAddress, recipient);

                    AirdropWhitelistEntity record = airdropWhitelistMapper.selectOne(whiteWrapper);
                    if (record != null && !record.getHasClaimed()) {
                        record.setHasClaimed(true);
                        record.setClaimTxHash(txHash);
                        record.setClaimTime(LocalDateTime.now());

                        int updateCount = airdropWhitelistMapper.updateById(record);

                        if (updateCount > 0) {
                            updateOnChainBalance(recipient);

                            BigDecimal airdropAmount = record.getAmount().setScale(0, BigDecimal.ROUND_HALF_UP);
                            notificationService.sendSystemNotification(recipient, "空投领取成功",
                                    String.format("您已成功领取 %s 个 EXTH 代币。", airdropAmount));

                            log.info("Airdrop claim synced for {} and notification sent", recipient);
                        } else {
                            log.warn("Failed to update airdrop claim record for {}", recipient);
                        }
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("Error handling AirdropClaimed event", e);
            throw new RuntimeException("Failed to process AirdropClaimed event", e);
        }
    }


    /**
     * 处理交易对创建事件
     */
    private void handleTradeCreateEvent(Map<String, Object> event) {
        try {
            String tradeIdStr = (String) event.get("tradeId");
            String chainTradeIdStr = (String) event.get("chainTradeId");
            BigInteger tradeId = new BigInteger(tradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeCreated event: tradeId={}, chainTradeId={}, txHash={}", tradeId, chainTradeIdStr, txHash);

            Long chainTradeId = Long.valueOf(chainTradeIdStr);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                ExchangeWrapper.TradeInfo info = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                if (info != null) {
                    log.info("Chain data - chainTradeId: {}, amount: {}, partyA: {}, partyB: {}",
                            chainTradeId, info.amount, info.partyA, info.partyB);

                    TradeRecordEntity existing = tradeMapper.selectOne(
                            new LambdaQueryWrapper<TradeRecordEntity>()
                                    .eq(TradeRecordEntity::getTradeId, tradeIdStr)
                    );

                    if (existing == null) {
                        log.error("Trade with tradeId={} not found in DB, skipping sync", tradeIdStr);
                        return;
                    }

                    existing.setChainTradeId(chainTradeId);

                    if (info.exthReward != null && info.exthReward.compareTo(BigInteger.ZERO) > 0) {
                        existing.setExthReward(fromChainUnit(info.exthReward));
                    }
                    if (info.feeAmount != null && info.feeAmount.compareTo(BigInteger.ZERO) > 0) {
                        existing.setFeeAmount(fromChainUnit(info.feeAmount));
                    }
                    existing.setStatus(info.state.intValue());
                    existing.setDisputeStatus(info.disputeStatus.intValue());

                    if (info.createTime != null && info.createTime.longValue() > 0) {
                        long createTimestamp = info.createTime.longValue();
                        LocalDateTime createDateTime = LocalDateTime.ofEpochSecond(
                                createTimestamp, 0, ZoneId.of("Asia/Shanghai").getRules().getOffset(Instant.now())
                        );
                        LocalDateTime expireDateTime = createDateTime.plusHours(TRADE_TIMEOUT_HOURS);
                        existing.setCreateTime(createDateTime);
                        existing.setExpireTime(expireDateTime);
                    }

                    int updateCount = tradeMapper.updateById(existing);

                    if (updateCount > 0) {
                        log.info("Trade {} (chainId: {}) updated from chain. Final amount: {}",
                                existing.getTradeId(), chainTradeId, existing.getAmount());

                        if (existing.getPartyA() != null && !existing.getPartyA().isEmpty()) {
                            notificationService.sendTradeNotification(existing.getPartyA(), existing.getTradeId(), "created");
                            log.info("Sent notification to PartyA: {}", existing.getPartyA());
                        } else {
                            log.error("️ PartyA address is EMPTY for trade {}", existing.getTradeId());
                        }

                        if (existing.getPartyB() != null && !existing.getPartyB().isEmpty()) {
                            notificationService.sendTradeNotification(existing.getPartyB(), existing.getTradeId(), "created");
                            log.info(" Sent notification to PartyB: {}", existing.getPartyB());
                        } else {
                            log.error("️ PartyB address is EMPTY for trade {}", existing.getTradeId());
                        }
                    } else {
                        log.warn("Failed to update trade {} in database", existing.getTradeId());
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("Error processing TradeCreated event", e);
            throw new RuntimeException("Failed to process TradeCreated event", e);
        }
    }


    /**
     * 处理交易完成事件
     */
    private void handleTradeCompletedEvent(Map<String, Object> event) {
        try {
            String chainTradeIdStr = (String) event.get("tradeId");
            Long chainTradeId = Long.parseLong(chainTradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeCompleted event: chainTradeId={}", chainTradeId);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    ExchangeWrapper.TradeInfo tradeInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                    if (tradeInfo != null) {
                        trade.setStatus(tradeInfo.state.intValue());
                        trade.setChainTxHash(txHash);

                        if (tradeInfo.completeTime != null && tradeInfo.completeTime.longValue() > 0) {
                            trade.setCompleteTime(
                                    LocalDateTime.ofEpochSecond(tradeInfo.completeTime.longValue(), 8, ZoneOffset.UTC)
                            );
                        }

                        int updateCount = tradeMapper.updateById(trade);

                        if (updateCount > 0) {
                            log.info("Trade {} (chainId: {}) marked as completed in DB.", trade.getTradeId(), chainTradeId);

                            updateOnChainBalance(trade.getPartyA());
                            updateOnChainBalance(trade.getPartyB());

                            notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "completed");
                            notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "completed");

                            log.info("Notifications sent for completed trade {}", trade.getTradeId());
                        } else {
                            log.warn("Failed to update trade {} completion status in database", trade.getTradeId());
                        }
                    }
                } else {
                    log.warn("Trade record not found in DB for chain ID: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        } catch (Exception e) {
            log.error("Error processing TradeCompleted event", e);
            throw new RuntimeException("Failed to process TradeCompleted event", e);
        }
    }


    /**
     * 查询并更新用户的链上EXTH余额
     */
    private void updateOnChainBalance(String address) {
        RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + address);
        try {
            if (!lock.tryLock(5, 10, SECONDS)) {
                log.warn("Balance update for {} locked, skip processing", address);
                return;
            }

            BigInteger chainBalance = exthWrapper.balanceOf(address);
            BigDecimal balance = fromChainUnit(chainBalance);

            LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(UserEntity::getAddress, address);
            UserEntity user = userMapper.selectOne(userWrapper);

            if (user != null) {
                user.setExthBalance(balance);
                userMapper.updateById(user);
                log.info("Updated EXTH balance after trade: address={}, balance={}", address, balance);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Balance update for {} interrupted", address, e);
        } catch (Exception e) {
            log.error("Failed to update balance from chain for address: {}", address, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    /**
     * 处理 MultiSigWallet 委员会成员添加事件
     */
    private void handleCommitteeMemberAddedEvent(Map<String, Object> event) {
        String txHash = (String) event.get("txHash");
        try {
            String memberAddress = (String) event.get("member");
            log.info("Processing CommitteeMemberAdded: {}", memberAddress);

            RLock lock = redissonClient.getLock(COMMITTEE_LOCK_KEY_PREFIX + memberAddress);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Committee member {} locked, skip processing", memberAddress);
                    return;
                }

                LambdaQueryWrapper<CommitteeMemberEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(CommitteeMemberEntity::getAddress, memberAddress);
                CommitteeMemberEntity existing = committeeMemberMapper.selectOne(wrapper);

                if (existing == null) {
                    CommitteeMemberEntity member = new CommitteeMemberEntity();
                    member.setAddress(memberAddress);
                    member.setIsActive(true);
                    member.setJoinTime(LocalDateTime.now());
                    member.setUpdateTime(LocalDateTime.now());

                    int insertCount = committeeMemberMapper.insert(member);

                    if (insertCount > 0) {
                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您已被任命为仲裁委员会成员",
                                "committee_appointed"
                        );

                        log.info("Committee member added and notification sent: {}", memberAddress);
                    } else {
                        log.warn("Failed to insert committee member: {}", memberAddress);
                    }
                } else {
                    existing.setIsActive(true);
                    existing.setUpdateTime(LocalDateTime.now());

                    int updateCount = committeeMemberMapper.updateById(existing);

                    if (updateCount > 0) {
                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您的仲裁委员会成员资格已恢复",
                                "committee_reactivated"
                        );

                        log.info("Committee member reactivated and notification sent: {}", memberAddress);
                    } else {
                        log.warn("Failed to update committee member: {}", memberAddress);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling CommitteeMemberAdded event", e);
            throw new RuntimeException("Failed to process CommitteeMemberAdded event", e);
        }
    }


    /**
     * 处理 MultiSigWallet 委员会成员移除事件
     */
    private void handleCommitteeMemberRemovedEvent(Map<String, Object> event) {
        String txHash = (String) event.get("txHash");
        try {
            String memberAddress = (String) event.get("member");
            log.info("Processing CommitteeMemberRemoved: {}", memberAddress);

            RLock lock = redissonClient.getLock(COMMITTEE_LOCK_KEY_PREFIX + memberAddress);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Committee member {} locked, skip processing", memberAddress);
                    return;
                }

                LambdaQueryWrapper<CommitteeMemberEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(CommitteeMemberEntity::getAddress, memberAddress);
                CommitteeMemberEntity existing = committeeMemberMapper.selectOne(wrapper);

                if (existing != null) {
                    existing.setIsActive(false);
                    existing.setLeaveTime(LocalDateTime.now());
                    existing.setUpdateTime(LocalDateTime.now());

                    int updateCount = committeeMemberMapper.updateById(existing);

                    if (updateCount > 0) {
                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您的仲裁委员会成员资格已被移除",
                                "committee_removed"
                        );

                        log.info("Committee member deactivated and notification sent: {}", memberAddress);
                    } else {
                        log.warn("Failed to deactivate committee member: {}", memberAddress);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling CommitteeMemberRemoved event", e);
            throw new RuntimeException("Failed to process CommitteeMemberRemoved event", e);
        }
    }



    /**
     * 处理 DAO 提案创建事件
     */
    private void handleDaoProposalCreatedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String proposer = (String) event.get("proposer");
            String txHash = (String) event.get("txHash");

            log.info("Processing DAO ProposalCreated: id={}, proposer={}", proposalId, proposer);

            RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("DAO proposal {} locked, skip processing", proposalId);
                    return;
                }

                // 先同步数据到数据库
                boolean syncSuccess = daoService.syncProposalFromChain(proposalId, proposer, txHash);

                // 只有同步成功后才发送通知
                if (syncSuccess) {
                    notificationService.sendDaoProposalNotification(
                            proposer,
                            proposalId,
                            "新提案 #" + proposalId,
                            "created"
                    );

                    log.info("DAO proposal {} synced and notification sent", proposalId);
                } else {
                    log.warn("Failed to sync DAO proposal {}: sync returned false", proposalId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling DAO ProposalCreated event", e);
            throw new RuntimeException("Failed to process DAO ProposalCreated event", e);
        }
    }


    /**
     * 处理 DAO 投票事件
     */
    private void handleDaoVoteCastEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);
            String voter = (String) event.get("voter");
            boolean support = Boolean.TRUE.equals(event.get("support"));


            log.info("Processing DAO VoteCast: proposalId={}", proposalId);

            RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalIdStr);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("DAO proposal {} locked, skip processing", proposalIdStr);
                    return;
                }

                // 先同步投票数据到数据库
                boolean syncSuccess = daoService.syncProposalVotesFromChain(proposalId);

                // 只有同步成功后才发送通知
                if (syncSuccess) {
                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalIdStr);
                    ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                    if (record != null) {
                        String action = "voted";
                        notificationService.sendDaoProposalNotification(
                                voter,
                                proposalIdStr,
                                "投票 #" + proposalId,
                                action
                        );

                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "新投票 #" + proposalId,
                                "received_vote"
                        );

                        log.info("DAO vote synced and notifications sent for proposal {}", proposalId);
                    }
                } else {
                    log.warn("Failed to sync DAO votes for proposal {}: sync returned false", proposalId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling DAO VoteCast event", e);
            throw new RuntimeException("Failed to process DAO VoteCast event", e);
        }
    }


    /**
     * 处理 DAO 提案入列（进入公示期）事件
     */
    private void handleDaoProposalQueuedEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);
            String etaStr = (String) event.get("eta");

            log.info("Processing DAO ProposalQueued: id={}", proposalId);

            RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalIdStr);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("DAO proposal {} locked, skip processing", proposalIdStr);
                    return;
                }

                // 更新数据库状态为 4 (Queued)
                LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProposalRecordEntity::getProposalId, proposalIdStr);
                ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                if (record != null) {
                    DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);
                    record.setEta(Long.parseLong(etaStr));
                    record.setStatus(info.status);

                    int updateCount = proposalRecordMapper.updateById(record);

                    if (updateCount > 0) {
                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 进入公示期",
                                "proposal_queued"
                        );

                        log.info("Proposal {} status updated to Queued and notification sent", proposalId);
                    } else {
                        log.warn("Failed to update proposal {} status to Queued", proposalId);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling DAO ProposalQueued event", e);
            throw new RuntimeException("Failed to process DAO ProposalQueued event", e);
        }
    }


    /**
     * 处理 DAO 提案执行事件
     */
    private void handleDaoProposalExecutedEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);

            log.info("Processing DAO ProposalExecuted: id={}", proposalId);

            RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalIdStr);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("DAO proposal {} locked, skip processing", proposalIdStr);
                    return;
                }

                DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);

                LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProposalRecordEntity::getProposalId, proposalIdStr);
                ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                if (record != null) {
                    record.setStatus(info.status); // Executed

                    int updateCount = proposalRecordMapper.updateById(record);

                    if (updateCount > 0) {
                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 已执行",
                                "proposal_executed"
                        );
                        log.info("Proposal {} marked as executed and notification sent", proposalId);

                        // 检查是否为空投提案并初始化配置
                        initAirdropConfigIfMatched(proposalIdStr, record.getDescription());
                    } else {
                        log.warn("Failed to update proposal {} status to Executed", proposalId);
                    }

                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling DAO ProposalExecuted event", e);
            throw new RuntimeException("Failed to process DAO ProposalExecuted event", e);
        }

    }


    /**
     * 如果提案描述包含特定关键词，则初始化空投配置
     */
    private void initAirdropConfigIfMatched(String proposalId, String description) {
        // 如果描述里包含 "Airdrop" 或 "空投"，则触发初始化
        if (description != null && (description.toLowerCase().contains("airdrop") || description.contains("空投"))) {
            try {
                // 读取 Merkle Root (从项目里的 merkle-output.json 读取)
                String merkleRoot = loadMerkleRootFromJson();

                Map<String, String> whitelistMap = loadWhitelistMap();

                BigDecimal totalAmount = whitelistMap.values().stream()
                        .map(BigDecimal::new)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 创建配置记录
                AirdropConfigEntity config = new AirdropConfigEntity();
                config.setProposalId(proposalId);
                config.setMerkleRoot(merkleRoot);
                config.setStatus(1); // 1-进行中
                config.setTokenAddress(exthContractAddress);
                config.setTotalAmount(totalAmount.divide(BigDecimal.TEN.pow(6)));
                LocalDateTime now = LocalDateTime.now();
                config.setStartTime(now);
                // 设置结束时间：设置为当前时间的 30 天后
                config.setEndTime(now.plusDays(30));
                config.setCreateTime(now);

                airdropConfigMapper.insert(config);
                log.info("Airdrop config created for proposal: {}", proposalId);

                // 导入白名单数据
                importWhitelistToDb(config.getId());

            } catch (Exception e) {
                log.error("Failed to init airdrop config", e);
            }
        }
    }

    private Map<String, String> loadWhitelistMap() throws Exception {
        String filePath = "D:/Users/asus/Desktop/区块链项目/exchange-system/exc-contracts/whitelist.json";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        return new ObjectMapper().readValue(content, new TypeReference<>() {
        });
    }

    /**
     * 从 JSON 文件加载 Merkle Root
     */
    private String loadMerkleRootFromJson() throws Exception {
        // 路径根据实际部署位置调整，这里指向 exc-contracts 目录下的输出文件
        String filePath = "D:/Users/asus/Desktop/区块链项目/exchange-system/exc-contracts/merkle-output.json";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        Map<String, Object> map = new ObjectMapper().readValue(content, new TypeReference<>() {
        });
        return (String) map.get("merkleRoot");
    }

    /**
     * 将 whitelist.json 导入数据库
     */
    private void importWhitelistToDb(Long configId) throws Exception {
        String filePath = "D:/Users/asus/Desktop/区块链项目/exchange-system/exc-contracts/whitelist.json";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        // 解析 JSON: {"address": "amount", ...}
        Map<String, String> whitelistMap = new ObjectMapper().readValue(content, new TypeReference<Map<String, String>>() {
        });

        List<AirdropWhitelistEntity> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : whitelistMap.entrySet()) {
            AirdropWhitelistEntity entity = new AirdropWhitelistEntity();
            entity.setConfigId(configId);
            entity.setAddress(entry.getKey());
            // JSON 里是带小数位的字符串，如果是整数需根据合约 decimals 转换
            entity.setAmount(new BigDecimal(entry.getValue()).divide(BigDecimal.TEN.pow(6)));
            entity.setHasClaimed(false);
            list.add(entity);
        }

        // 批量插入 (每 500 条一次)
        int batchSize = 500;
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            List<AirdropWhitelistEntity> subList = list.subList(i, end);
            // MyBatis-Plus 默认没有 saveBatch，需要注入 Service 或使用循环插入
            for (AirdropWhitelistEntity item : subList) {
                airdropWhitelistMapper.insert(item);
            }
        }
        log.info(" Imported {} whitelist entries for config: {}", list.size(), configId);
    }

    /**
     * 处理 DAO 提案取消事件
     */
    private void handleDaoProposalCanceledEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);

            log.info("Processing DAO ProposalCanceled: id={}", proposalId);

            RLock lock = redissonClient.getLock(DAO_SYNC_LOCK_KEY_PREFIX + proposalIdStr);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("DAO proposal {} locked, skip processing", proposalIdStr);
                    return;
                }

                DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);

                LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProposalRecordEntity::getProposalId, proposalIdStr);
                ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                if (record != null) {
                    record.setStatus(info.status); // Canceled
                    int updateCount = proposalRecordMapper.updateById(record);

                    if (updateCount > 0) {
                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 已取消",
                                "proposal_canceled"
                        );

                        log.info("Proposal {} marked as canceled", proposalId);
                    } else {
                        log.warn("Failed to update proposal {} in database", proposalId);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling DAO ProposalCanceled event", e);
            throw new RuntimeException("Failed to process DAO ProposalCanceled event", e);
        }
    }



    /**
     * 处理用户拉黑事件
     */
    private void handleUserBlacklistEvent(Map<String, Object> event) {
        try {
            String user = (String) event.get("user");
            String txHash = (String) event.get("txHash");
            log.info("Processing UserBlacklisted: user={}, txHash={}", user, txHash);

            RLock lock = redissonClient.getLock(USER_LOCK_KEY_PREFIX + user);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("User {} locked, skip processing", user);
                    return;
                }

                UserEntity userEntity = userMapper.selectByAddress(user);
                if (userEntity != null && !userEntity.getIsBlacklisted()) {
                    userEntity.setIsBlacklisted(true);
                    userEntity.setUpdateTime(LocalDateTime.now());

                    int updateCount = userMapper.updateById(userEntity);

                    if (updateCount > 0) {
                        notificationService.sendSystemNotification(user, "账户受限通知",
                                "由于您的账户存在违规行为，已被列入系统黑名单。如有疑问请联系仲裁委员会。");
                        log.info("User {} blacklisted in DB and notification sent.", user);
                    } else {
                        log.warn("Failed to blacklist user {}", user);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling UserBlacklist event", e);
            throw new RuntimeException("Failed to process UserBlacklist event", e);
        }
    }



    /**
     * 处理手续费收取事件
     */
    private void handleFeeCollectedEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            String payerA = (String) event.get("feePayerA");
            String payerB = (String) event.get("feePayerB");
            String feeAmount = String.valueOf(event.get("feeAmount"));

            log.info("Processing FeeCollected: chainTradeId={}, payers={}/{}", chainTradeId, payerA, payerB);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 使用 LambdaQueryWrapper 精确匹配 chain_trade_id 字段
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 通知 PartyA
                    notificationService.sendSystemNotification(payerA, "手续费扣除通知",
                            String.format("您的交易 #%s 已成功完成，系统已自动扣除 %s EXTH 作为手续费。", trade.getTradeId(), feeAmount));

                    // 通知 PartyB
                    notificationService.sendSystemNotification(payerB, "手续费扣除通知",
                            String.format("您的交易 #%s 已成功完成，系统已自动扣除 %s EXTH 作为手续费。", trade.getTradeId(), feeAmount));

                    log.info("Fee collection notifications sent for local trade {}", trade.getTradeId());
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process fee collected event", e);
            throw new RuntimeException("Failed to process fee collected event", e);
        }
    }


    /**
     * 处理赔偿支付事件 (由 Treasure 合约触发)
     */
    private void handleCompensationPaidEvent(Map<String, Object> event) {
        try {
            // 这里的 tradeId 实际上是链上的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            String recipient = (String) event.get("recipient");
            String amount = String.valueOf(event.get("amount"));
            String txHash = (String) event.get("txHash");

            log.info("Processing CompensationPaid: chainTradeId={}, recipient={}, amount={}", chainTradeId, recipient, amount);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地交易记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 通知收款方（使用本地业务ID）
                    notificationService.sendSystemNotification(recipient, "仲裁赔偿金到账",
                            String.format("关于订单 %s 的仲裁赔偿款 %s USDT 已打入您的账户。交易哈希: %s",
                                    trade.getTradeId(), amount, txHash));

                    log.info("Compensation notifications sent for local trade {}", trade.getTradeId());
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process compensation paid event", e);
            throw new RuntimeException("Failed to process compensation paid event", e);
        }
    }


    /**
     * 处理仲裁提案创建事件
     */
    private void handleArbitrationProposalCreatedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String chainTradeId = String.valueOf(event.get("tradeId"));
            String accusedAddress = String.valueOf(event.get("accusedParty"));
            String txHash = (String) event.get("txHash");

            log.info("Processing ArbitrationProposalCreated: proposalId={}, tradeId={}, accused={}",
                    proposalId, chainTradeId, accusedAddress);

            RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Arbitration proposal {} locked, skip processing", proposalId);
                    return;
                }

                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>()
                                .eq("chain_trade_id", chainTradeId)
                );

                if (dispute != null) {
                    dispute.setProposalId(proposalId);
                    dispute.setProposalTxHash(txHash);
                    dispute.setAccused(accusedAddress);
                    dispute.setInitiator(dispute.getInitiator());

                    try {
                        DisputeDTO chainDetails = multiSigWalletService.getProposalDetails(new BigInteger(proposalId));
                        if (chainDetails != null) {
                            // 直接从链上同步提案状态、票数、截止时间等所有字段
                            dispute.setProposalStatus(chainDetails.getStatus());
                            dispute.setVoteCount(chainDetails.getVoteCount());
                            dispute.setRejectCount(chainDetails.getRejectCount());
                            dispute.setCompensationAmount(chainDetails.getCompensationAmount());
                            dispute.setDeadline(chainDetails.getDeadline());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch proposal details from chain for ID: {}", proposalId, e);
                    }

                    int updateCount = disputeRecordMapper.updateById(dispute);

                    if (updateCount > 0) {
                        log.info("Dispute record {} linked to proposal {}", dispute.getId(), proposalId);

                        notificationService.sendSystemNotification(accusedAddress, "仲裁通知",
                                String.format("您有一笔关于订单 %s 的仲裁提案已提交至仲裁委员会。提案ID: %s。",
                                        chainTradeId, proposalId));

                        if (dispute.getInitiator() != null) {
                            notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁进度",
                                    String.format("您发起的关于订单 %s 的仲裁已进入审理阶段。提案ID: %s。", chainTradeId, proposalId));
                        }

                        List<CommitteeMemberEntity> members = committeeMemberMapper.selectList(
                                new QueryWrapper<CommitteeMemberEntity>().eq("is_active", 1)
                        );

                        for (CommitteeMemberEntity member : members) {
                            notificationService.sendSystemNotification(member.getAddress(), "待处理仲裁任务",
                                    String.format("新的仲裁提案 %s 已创建，涉及订单 %s。请登录后台查看详细信息并进行投票。",
                                            proposalId, chainTradeId));
                        }

                        log.info("Arbitration proposal notifications sent for proposal {}", proposalId);
                    } else {
                        log.warn("Failed to update dispute record for proposal {}", proposalId);
                    }
                } else {
                    log.warn("No dispute record found for tradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalCreated event", e);
            throw new RuntimeException("Failed to process ArbitrationProposalCreated event", e);
        }
    }


    /**
     * 处理仲裁投票事件
     */
    private void handleArbitrationProposalVotedEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");

            String proposalId = String.valueOf(event.get("proposalId"));
            String voter = (String) event.get("voter");
            boolean support = Boolean.TRUE.equals(event.get("support"));


            log.info("Processing ArbitrationProposalVoted: proposalId={}", proposalId);

            RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Arbitration proposal {} locked, skip processing", proposalId);
                    return;
                }

                // 直接从链上获取最新的票数和状态
                MultiSigWalletWrapper.ProposalInfo chainProposal = multiSigWalletWrapper.getProposalDetails(new BigInteger(proposalId));

                // 更新数据库
                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>()
                                .eq("proposal_id", proposalId)
                );

                if (dispute != null) {
                    dispute.setVoteCount(chainProposal.getVoteCount().intValue());
                    dispute.setRejectCount(chainProposal.getRejectCount().intValue());
                    dispute.setProposalStatus(chainProposal.getStatus()); // 状态可能随投票变为 Executed

                    int updateCount = disputeRecordMapper.updateById(dispute);

                    if (updateCount > 0) {
                        // 发送通知给争议双方
                        String voteAction = support ? "支持" : "反对";
                        String content = String.format("仲裁委员会成员 %s 已对您的仲裁提案 %s 投出 %s 票。当前票数 - 赞成: %d, 反对: %d。",
                                voter, proposalId, voteAction, chainProposal.getVoteCount(), chainProposal.getRejectCount());

                        // 通知申诉方 (Initiator/Victim)
                        if (dispute.getInitiator() != null) {
                            notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁投票进度", content);
                        }

                        // 通知被指控方 (Accused)
                        if (dispute.getAccused() != null) {
                            notificationService.sendSystemNotification(dispute.getAccused(), "仲裁投票进度", content);
                        }

                        log.info("Arbitration vote notifications sent for proposal {}", proposalId);
                    } else {
                        log.warn("Failed to update dispute record for proposal {}", proposalId);
                    }
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalVoted event", e);
            throw new RuntimeException("Failed to process ArbitrationProposalVoted event", e);
        }
    }


    /**
     * 处理仲裁提案过期事件
     */
    private void handleArbitrationProposalExpiredEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String txHash = (String) event.get("txHash");

            log.info("Processing ArbitrationProposalExpired: proposalId={}, txHash={}", proposalId, txHash);

            RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Arbitration proposal {} locked, skip processing", proposalId);
                    return;
                }

                // 从链上获取最新的提案状态和详情
                MultiSigWalletWrapper.ProposalInfo chainProposal = multiSigWalletWrapper.getProposalDetails(new BigInteger(proposalId));

                // 查找对应的争议记录
                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>().eq("proposal_id", proposalId)
                );

                if (dispute != null) {
                    // 同步链上的最新状态和票数
                    dispute.setProposalStatus(chainProposal.status);
                    dispute.setVoteCount(chainProposal.voteCount.intValue());
                    dispute.setRejectCount(chainProposal.rejectCount.intValue());
                    dispute.setResolveTime(LocalDateTime.now());

                    String resultMsg = String.format("仲裁提案已过期。投票超时未达成共识，赞成票: %d, 反对票: %d, 交易哈希: %s",
                            chainProposal.voteCount, chainProposal.rejectCount, txHash);
                    dispute.setResult(resultMsg);

                    int disputeUpdateCount = disputeRecordMapper.updateById(dispute);

                    // 同步更新关联的交易记录状态 - 从 Exchange 合约获取最新争议状态
                    boolean tradeUpdated = false;
                    TradeRecordEntity trade = tradeMapper.selectOne(
                            new LambdaQueryWrapper<TradeRecordEntity>()
                                    .eq(TradeRecordEntity::getChainTradeId, chainProposal.tradeId.longValue())
                    );

                    if (trade != null) {
                        // 从 Exchange 合约获取最新状态
                        ExchangeWrapper.TradeInfo chainTradeInfo = exchangeContract.getTradeInfo(chainProposal.tradeId);
                        trade.setDisputeStatus(chainTradeInfo.disputeStatus.intValue());
                        int tradeUpdateCount = tradeMapper.updateById(trade);
                        tradeUpdated = tradeUpdateCount > 0;
                    }

                    if (disputeUpdateCount > 0) {
                        // 通知交易双方
                        String msg = String.format("关于订单 %s 的仲裁提案 %s 已过期，投票时间结束但未达成共识。",
                                chainProposal.tradeId, proposalId);

                        if (dispute.getInitiator() != null) {
                            notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁结果通知", msg);
                        }
                        if (dispute.getAccused() != null) {
                            notificationService.sendSystemNotification(dispute.getAccused(), "仲裁结果通知", msg);
                        }

                        log.info("Arbitration proposal {} expiration synced from chain. Status: {}", proposalId, chainProposal.status);
                    } else {
                        log.warn("Failed to update dispute record for expired proposal {}", proposalId);
                    }
                } else {
                    log.warn("No dispute record found for expired proposal: {}", proposalId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalExpired event", e);
            throw new RuntimeException("Failed to process ArbitrationProposalExpired event", e);
        }
    }




    /**
     * 处理奖励发放事件
     */
    private void handleRewardEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String user = (String) event.get("user");
            BigInteger amount = new BigInteger((String) event.get("amount"));
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = (String) event.get("tradeId");
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            log.info("Processing RewardDistributed: user={}, amount={}, chainTradeId={}", user, amount, chainTradeId);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地交易记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );


                String displayTradeId = trade.getTradeId();
                String rewardMsg = String.format("订单 %s 已完成，您获得了 %s EXTH 的交易奖励。", displayTradeId, amount.toString());

                // 通知交易双方
                notificationService.sendSystemNotification(trade.getPartyA(), "交易奖励到账", rewardMsg);
                notificationService.sendSystemNotification(trade.getPartyB(), "交易奖励到账", rewardMsg);

                log.info("Reward notifications sent for trade {}", displayTradeId);
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process reward event", e);
            throw new RuntimeException("Failed to process reward event", e);
        }
    }


    /**
     * 处理用户类型升级事件
     */
    private void handleUserUpgradedEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            String userAddress = (String) event.get("user");
            Object typeObj = event.get("newType");
            int newUserType = typeObj instanceof Number ? ((Number) typeObj).intValue() : Integer.parseInt(typeObj.toString());

            log.info("Processing UserUpgraded: user={}, newType={}", userAddress, newUserType);

            RLock lock = redissonClient.getLock(USER_LOCK_KEY_PREFIX + userAddress);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("User {} locked, skip processing", userAddress);
                    return;
                }

                // 查找或创建用户
                UserEntity user = userMapper.selectByAddress(userAddress);
                boolean isNewUser = false;
                if (user == null) {
                    user = new UserEntity();
                    user.setAddress(userAddress);
                    user.setRegisterTime(LocalDateTime.now());
                    isNewUser = true;
                }

                // 直接同步链上状态到数据库
                user.setUserType(newUserType);
                user.setLastActiveTime(LocalDateTime.now());
                user.setUpdateTime(LocalDateTime.now());

                // 从链上同步 newUserTradeCount，确保与合约状态完全一致
                try {
                    ExchangeWrapper.UserInfo chainUserInfo = exchangeContract.getUserInfo(userAddress);
                    if (chainUserInfo != null && chainUserInfo.newUserTradeCount != null) {
                        user.setNewUserTradeCount(chainUserInfo.newUserTradeCount.intValue());
                    }
                } catch (Exception e) {
                    log.warn("Failed to sync newUserTradeCount for user: {}", userAddress, e);
                }
                try {
                    BigInteger exthBalance = exthWrapper.balanceOf(userAddress);
                    user.setExthBalance(fromChainUnit(exthBalance));
                    log.info("Synced EXTH balance for {}: {}", userAddress, exthBalance);
                } catch (Exception e) {
                    log.warn("Failed to get EXTH balance for {}, using 0", userAddress);
                    if (user.getExthBalance() == null) {
                        user.setExthBalance(BigDecimal.ZERO);
                    }
                }

                // 保存更新并检查结果
                int updateCount;
                if (isNewUser) {
                    updateCount = userMapper.insert(user);
                } else {
                    updateCount = userMapper.updateById(user);
                }

                if (updateCount > 0) {
                    notificationService.sendUserUpgradeNotification(userAddress, newUserType);
                    log.info("User {} synced from chain and notification sent: type {}", userAddress, newUserType);
                } else {
                    log.warn("Failed to save user {} data", userAddress);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling UserUpgraded event", e);
            throw new RuntimeException("Failed to process UserUpgraded event", e);
        }
    }



    /**
     * 处理甲方确认事件
     */
    private void handlePartyAConfirmedEvent(Map<String, Object> event) {
        try {
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing PartyBConfirmed event: chainTradeId={}, txHash={}", chainTradeId, txHash);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 从链上查询最新状态以同步数据
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                    // 直接使用链上返回的 state 值
                    trade.setStatus(chainInfo.state.intValue());
                    trade.setPartyAConfirmTime(LocalDateTime.now());

                    int updateCount = tradeMapper.updateById(trade);

                    if (updateCount > 0) {
                        log.info("Trade {} (chainId: {}) status synced from chain and notification sent: state={}", trade.getTradeId(), chainTradeId, chainInfo.state);

                        // 通知乙方（使用本地业务ID展示）
                        notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "party_a_confirmed");
                    } else {
                        log.warn("Failed to update trade {} status", trade.getTradeId());
                    }
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling PartyAConfirmed event", e);
            throw new RuntimeException("Failed to process PartyAConfirmed event", e);
        }
    }



    /**
     * 处理乙方确认事件
     */
    private void handlePartyBConfirmedEvent(Map<String, Object> event) {
        try {
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing PartyBConfirmed event: chainTradeId={}, txHash={}", chainTradeId, txHash);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 从链上查询最新状态以同步数据
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                    // 直接使用链上返回的 state 值
                    trade.setStatus(chainInfo.state.intValue());
                    trade.setPartyBConfirmTime(LocalDateTime.now());

                    int updateCount = tradeMapper.updateById(trade);

                    if (updateCount > 0) {
                        log.info("Trade {} (chainId: {}) status synced from chain and notification sent: state={}", trade.getTradeId(), chainTradeId, chainInfo.state);

                        // 通知甲方（使用本地业务ID展示）
                        notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "party_b_confirmed");
                    } else {
                        log.warn("Failed to update trade {} status", trade.getTradeId());
                    }
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling PartyBConfirmed event", e);
            throw new RuntimeException("Failed to process PartyBConfirmed event", e);
        }
    }



    /**
     * 处理交易取消事件
     */
    private void handleTradeCancelledEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            log.info("Processing TradeCancelled event: chainTradeId={}", chainTradeId);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 从链上查询最新状态以同步数据
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                    // 使用链上返回的状态值
                    trade.setStatus(chainInfo.state.intValue());
                    // 使用链上的完成时间戳
                    if (chainInfo.completeTime != null && chainInfo.completeTime.longValue() > 0) {
                        trade.setCompleteTime(
                                LocalDateTime.ofEpochSecond(chainInfo.completeTime.longValue(), 8, ZoneOffset.UTC)
                        );
                    }

                    int updateCount = tradeMapper.updateById(trade);

                    if (updateCount > 0) {
                        log.info("Trade {} (chainId: {}) status synced from chain and notifications sent: state={}", trade.getTradeId(), chainTradeId, chainInfo.state);

                        // 通知双方（使用本地业务ID展示）
                        notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "cancelled");
                        notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "cancelled");
                    } else {
                        log.warn("Failed to update trade {} status", trade.getTradeId());
                    }
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling TradeCancelled event", e);
            throw new RuntimeException("Failed to process TradeCancelled event", e);
        }
    }



    /**
     * 处理交易争议事件
     */
    private void handleTradeDisputedEvent(Map<String, Object> event) {
        try {
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            String disputedParty = (String) event.get("disputedParty");
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeDisputed event: chainTradeId={}, disputedParty={}", chainTradeId, disputedParty);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 从链上查询最新状态以同步数据
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));

                    // 直接使用链上返回的状态值（5-Disputed）
                    trade.setStatus(chainInfo.state.intValue());
                    trade.setDisputedParty(chainInfo.disputedParty);
                    // 从链上同步争议状态
                    trade.setDisputeStatus(chainInfo.disputeStatus.intValue());

                    int tradeUpdateCount = tradeMapper.updateById(trade);

                    // 更新或创建争议记录（关联本地业务ID）
                    DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                            new QueryWrapper<DisputeRecordEntity>().eq("chain_trade_id", chainTradeId)
                    );

                    boolean isNewDispute = false;
                    if (dispute == null) {
                        dispute = new DisputeRecordEntity();
                        dispute.setChainTradeId(trade.getChainTradeId().toString());

                        // 根据 disputedParty 判断发起方和被指控方
                        String disputedPartyAddr = chainInfo.disputedParty != null ? chainInfo.disputedParty.toLowerCase() : "";
                        String partyAAddr = trade.getPartyA().toLowerCase();
                        String partyBAddr = trade.getPartyB().toLowerCase();

                        if (disputedPartyAddr.equals(partyAAddr)) {
                            // A 被指控，B 是发起方
                            dispute.setInitiator(trade.getPartyB());
                            dispute.setAccused(trade.getPartyA());
                        } else if (disputedPartyAddr.equals(partyBAddr)) {
                            // B 被指控，A 是发起方
                            dispute.setInitiator(trade.getPartyA());
                            dispute.setAccused(trade.getPartyB());
                        } else {
                            // 默认情况：将非 disputedParty 的一方设为发起方
                            dispute.setInitiator(trade.getPartyA());
                            dispute.setAccused(chainInfo.disputedParty != null ? chainInfo.disputedParty : trade.getPartyB());
                        }

                        // 设置默认争议原因（链上事件没有提供具体原因）
                        dispute.setReason("用户通过链上交易发起争议，等待仲裁委员会处理");
                        dispute.setEvidence(txHash != null ? txHash : "N/A");

                        dispute.setCreateTime(LocalDateTime.now());
                        isNewDispute = true;
                    }

                    // 保存争议记录
                    int disputeUpdateCount;
                    if (isNewDispute) {
                        disputeUpdateCount = disputeRecordMapper.insert(dispute);
                    } else {
                        disputeUpdateCount = disputeRecordMapper.updateById(dispute);
                    }

                    if (tradeUpdateCount > 0 && disputeUpdateCount > 0) {
                        log.info("Trade {} (chainId: {}) status synced from chain and notifications sent: state={}, disputeStatus={}",
                                trade.getTradeId(), chainTradeId, chainInfo.state, chainInfo.disputeStatus);

                        // 通知双方（使用本地业务ID展示）
                        notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "disputed");
                        notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "disputed");
                    } else {
                        log.warn("Failed to update trade or dispute record for chainTradeId: {}", chainTradeId);
                    }
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling TradeDisputed event", e);
            throw new RuntimeException("Failed to process TradeDisputed event", e);
        }
    }



    /**
     * 处理交易过期事件
     */
    private void handleTradeExpiredEvent(Map<String, Object> event) {
        try {
            String txHash = (String) event.get("txHash");
            // 这里的 tradeId 实际上是合约 emit 出来的 chainTradeId
            String chainTradeIdStr = String.valueOf(event.get("tradeId"));
            Long chainTradeId = Long.parseLong(chainTradeIdStr);

            log.info("Processing TradeExpired event: chainTradeId={}", chainTradeId);

            RLock lock = redissonClient.getLock(TRADE_LOCK_KEY_PREFIX + chainTradeId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Trade {} locked, skip processing", chainTradeId);
                    return;
                }

                // 根据链上 ID 查找本地记录
                TradeRecordEntity trade = tradeMapper.selectOne(
                        new LambdaQueryWrapper<TradeRecordEntity>()
                                .eq(TradeRecordEntity::getChainTradeId, chainTradeId)
                );

                if (trade != null) {
                    // 从链上同步最新状态
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(BigInteger.valueOf(chainTradeId));
                    if (chainInfo != null) {
                        trade.setStatus(chainInfo.state.intValue());
                    }

                    int updateCount = tradeMapper.updateById(trade);

                    if (updateCount > 0) {
                        log.info("Trade {} (chainId: {}) marked as expired in DB and notifications sent.", trade.getTradeId(), chainTradeId);

                        // 通知交易双方（使用本地业务ID展示）
                        notificationService.sendTradeNotification(trade.getPartyA(), trade.getTradeId(), "expired");
                        notificationService.sendTradeNotification(trade.getPartyB(), trade.getTradeId(), "expired");
                    } else {
                        log.warn("Failed to update trade {} expiration status", trade.getTradeId());
                    }
                } else {
                    log.warn("Local trade record not found for chainTradeId: {}", chainTradeId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling TradeExpired event", e);
            throw new RuntimeException("Failed to process TradeExpired event", e);
        }
    }



    /**
     * 处理仲裁提案执行事件
     */
    private void handleArbitrationProposalExecutedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String txHash = (String) event.get("txHash");

            log.info("Processing ArbitrationProposalExecuted: proposalId={}, txHash={}", proposalId, txHash);

            RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Arbitration proposal {} locked, skip processing", proposalId);
                    return;
                }

                // 查找对应的争议记录
                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>().eq("proposal_id", proposalId)
                );

                if (dispute != null) {
                    // 尝试从链上获取最终信息同步
                    try {
                        DisputeDTO details = multiSigWalletService.getProposalDetails(new BigInteger(proposalId));
                        // 同步链上状态 ( Executed)
                        dispute.setProposalStatus(details.getStatus());

                        dispute.setVoteCount(details.getVoteCount());
                        dispute.setRejectCount(details.getRejectCount());
                        dispute.setResolveTime(LocalDateTime.now());

                        // 设置处理结果
                        String resultMsg = String.format("仲裁裁决已执行。赞成票: %d, 反对票: %d, 赔偿金额: %s USDT, 交易哈希: %s",
                                details.getVoteCount(), details.getRejectCount(),
                                dispute.getCompensationAmount() != null ? dispute.getCompensationAmount().toString() : "0",
                                txHash);
                        dispute.setResult(resultMsg);

                    } catch (Exception e) {
                        log.warn("Failed to fetch final details for executed proposal: {}", proposalId, e);
                    }

                    int disputeUpdateCount = disputeRecordMapper.updateById(dispute);

                    // 同步更新关联的交易记录状态
                    boolean tradeUpdated = false;
                    TradeRecordEntity trade = tradeMapper.selectOne(
                            new LambdaQueryWrapper<TradeRecordEntity>()
                                    .eq(TradeRecordEntity::getChainTradeId, dispute.getChainTradeId())
                    );

                    if (trade != null) {
                        // 调用 exchangeContract.getTradeInfo 来获取 Exchange 合约中该交易的最終状态
                        try {
                            ExchangeWrapper.TradeInfo chainTradeInfo = exchangeContract.getTradeInfo(new BigInteger(trade.getTradeId()));
                            trade.setStatus(chainTradeInfo.state.intValue());
                            trade.setCompleteTime(
                                    LocalDateTime.ofEpochSecond(chainTradeInfo.completeTime.longValue(), 8, ZoneOffset.UTC)
                            );
                            trade.setDisputeStatus(chainTradeInfo.disputeStatus.intValue());

                            int tradeUpdateCount = tradeMapper.updateById(trade);
                            tradeUpdated = tradeUpdateCount > 0;
                        } catch (Exception e) {
                            log.error("Failed to sync trade info from chain", e);
                        }
                    }

                    if (disputeUpdateCount > 0) {
                        log.info("Arbitration proposal {} executed and synced from chain", proposalId);

                        // 更新交易双方的链上余额
                        updateOnChainBalance(dispute.getInitiator());
                        updateOnChainBalance(dispute.getAccused());

                        // 通知交易双方
                        if (dispute.getInitiator() != null) {
                            notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁结果通知",
                                    String.format("关于订单 %s 的仲裁提案 %s 已执行，裁决结果：%s",
                                            dispute.getChainTradeId(), proposalId, dispute.getResult()));
                        }
                        if (dispute.getAccused() != null) {
                            notificationService.sendSystemNotification(dispute.getAccused(), "仲裁结果通知",
                                    String.format("关于订单 %s 的仲裁提案 %s 已执行，裁决结果：%s",
                                            dispute.getChainTradeId(), proposalId, dispute.getResult()));
                        }
                    } else {
                        log.warn("Failed to update dispute record for executed proposal {}", proposalId);
                    }

                } else {
                    log.warn("No dispute record found for executed proposal: {}", proposalId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalExecuted event", e);
            throw new RuntimeException("Failed to process ArbitrationProposalExecuted event", e);
        }
    }



    /**
     * 处理仲裁提案拒绝事件
     */
    private void handleArbitrationProposalRejectedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String txHash = (String) event.get("txHash");


            log.info("Processing ArbitrationProposalRejected: proposalId={}, txHash={}", proposalId, txHash);

            RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
            try {
                if (!lock.tryLock(10, 30, SECONDS)) {
                    log.warn("Arbitration proposal {} locked, skip processing", proposalId);
                    return;
                }

                // 从链上获取最新的提案状态
                MultiSigWalletWrapper.ProposalInfo chainProposal = multiSigWalletWrapper.getProposalDetails(new BigInteger(proposalId));

                // 查找对应的争议记录
                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>().eq("proposal_id", proposalId)
                );

                if (dispute != null) {
                    // 从链上同步最新状态和票数
                    dispute.setProposalStatus(chainProposal.status);
                    dispute.setVoteCount(chainProposal.voteCount.intValue());
                    dispute.setRejectCount(chainProposal.rejectCount.intValue());
                    dispute.setResolveTime(LocalDateTime.now());

                    String resultMsg = String.format("仲裁提案已被拒绝。赞成票: %d, 反对票: %d, 交易哈希: %s",
                            chainProposal.voteCount, chainProposal.rejectCount, txHash);
                    dispute.setResult(resultMsg);

                    int disputeUpdateCount = disputeRecordMapper.updateById(dispute);

                    // 同步更新关联的交易记录状态
                    boolean tradeUpdated = false;
                    TradeRecordEntity trade = tradeMapper.selectOne(
                            new LambdaQueryWrapper<TradeRecordEntity>()
                                    .eq(TradeRecordEntity::getChainTradeId, Long.parseLong(dispute.getChainTradeId()))
                    );

                    if (trade != null) {
                        // 从 Exchange 合约获取最新状态
                        try {
                            BigInteger chainId = BigInteger.valueOf(trade.getChainTradeId());
                            ExchangeWrapper.TradeInfo chainTradeInfo = exchangeContract.getTradeInfo(chainId);

                            trade.setStatus(chainTradeInfo.state.intValue());
                            trade.setCompleteTime(
                                    LocalDateTime.ofEpochSecond(chainTradeInfo.completeTime.longValue(), 8, ZoneOffset.UTC)
                            );

                            trade.setDisputeStatus(chainTradeInfo.disputeStatus.intValue());

                            int tradeUpdateCount = tradeMapper.updateById(trade);
                            tradeUpdated = tradeUpdateCount > 0;
                        } catch (Exception e) {
                            log.warn("Failed to fetch trade info from Exchange contract for rejected proposal", e);
                        }
                    }

                    if (disputeUpdateCount > 0) {
                        // 通知交易双方
                        String msg = String.format("关于订单 %s 的仲裁提案 %s 已被仲裁委员会拒绝。",
                                dispute.getChainTradeId(), proposalId);

                        if (dispute.getInitiator() != null) {
                            notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁结果通知", msg);
                        }
                        if (dispute.getAccused() != null) {
                            notificationService.sendSystemNotification(dispute.getAccused(), "仲裁结果通知", msg);
                        }

                        log.info("Arbitration proposal {} rejection synced and notifications sent.", proposalId);
                    } else {
                        log.warn("Failed to update dispute record for rejected proposal {}", proposalId);
                    }
                } else {
                    log.warn("No dispute record found for rejected proposal: {}", proposalId);
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalRejected event", e);
            throw new RuntimeException("Failed to process ArbitrationProposalRejected event", e);
        }
    }

}