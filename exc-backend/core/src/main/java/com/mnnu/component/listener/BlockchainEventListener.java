package com.mnnu.component.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.component.handler.CustomWebSocketHandler;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.entity.*;
import com.mnnu.mapper.*;
import com.mnnu.service.*;
import com.mnnu.wrapper.AirdropWrapper;
import com.mnnu.wrapper.DaoWrapper;
import com.mnnu.wrapper.ExchangeWrapper;
import com.mnnu.wrapper.TreasureWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.mnnu.constant.SystemConstants.DisputeStatus.PENDING;
import static com.mnnu.constant.SystemConstants.TradeConstants.TRADE_TIMEOUT_HOURS;
import static com.mnnu.constant.SystemConstants.TradeStatus.RESOLVED;

/**
 * 区块链事件监听器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainEventListener {

    /**
     * 区块链服务
     */
    private final BlockchainService blockchainService;
    /**
     * 交易服务
     */
    private final TradeService tradeService;
    /**
     * 用户服务
     */
    private final UserService userService;
    /**
     * 空投服务
     */
    private final AirdropService airdropService;
    /**
     * 通知服务
     */
    private final NotificationService notificationService;
    /**
     * 对象映射器
     */
    private final ObjectMapper objectMapper;


    /**
     * Web3j 客户端
     */
    @Autowired
    private Web3j web3j;

    /**
     * 空投合约包装器
     */
    @Autowired
    private AirdropWrapper airdropWrapper;

    /**
     * DAO 合约包装器
     */
    @Autowired
    private DaoWrapper daoWrapper;

    /**
     * 财库合约包装器
     */
    @Autowired
    private TreasureWrapper treasureWrapper;

    /**
     * Exchange 合约包装器
     */
    @Autowired
    private ExchangeWrapper exchangeContract;

    /**
     * RabbitMQ 模板
     */
    @Autowired
    private RabbitTemplate rabbitTemplate;
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

    // application.yml 注入合约地址
    @Value("${contracts.exth}")
    private String exthContractAddress;


    /**
     * 处理区块链事件
     * - Transfer: 代币转账事件
     * - TradeMatched: 交易匹配事件
     * - TradeCompleted: 交易完成事件
     * - AirdropClaimed: 空投领取事件
     * - UserBlacklisted: 用户拉黑事件
     * - RewardDistributed: 奖励发放事件
     * - ProposalExecuted: 提案执行事件
     */

    @RabbitListener(queues = SystemConstants.MQQueue.BLOCKCHAIN_EVENT)
    public void handleBlockchainEvent(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            log.warn("Received empty blockchain event");
            return;
        }

        try {
            String eventType = (String) event.get("event");
            log.info("Received blockchain event: type={}, data={}", eventType, event);

            switch (eventType) {
                case "Transfer":
                    handleTransferEvent(event);
                    break;
                case "TradeMatched":
                    handleTradeMatchedEvent(event);
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
                case "DisputeResolved":
                    handleDisputeResolvedEvent(event);
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
                // 多签钱包（仲裁）相关事件
                case "ArbitrationProposalCreated":
                    handleArbitrationProposalCreatedEvent(event);
                    break;
                case "ArbitrationProposalVoted":
                    handleArbitrationProposalVotedEvent(event);
                    break;
                case "ArbitrationProposalFinalized":
                    handleArbitrationProposalFinalizedEvent(event);
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing blockchain event: {}", event, e);
        }
    }

    /**
     * 处理转账事件
     * 通知策略：实时推送到账通知
     */
    private void handleTransferEvent(Map<String, Object> event) {
        try {
            String from = (String) event.get("from");
            String to = (String) event.get("to");
            String valueStr = (String) event.get("value");
            BigInteger value = new BigInteger(valueStr);
            String txHash = (String) event.get("txHash");
            Number timestamp = (Number) event.get("timestamp");

            log.info("Processing Transfer event: from={}, to={}, value={}", from, to, value);

            // 1. 更新用户余额（从链上同步）
            userService.updateExthBalanceOnChain(to);
            if (from != null && !from.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
                userService.updateExthBalanceOnChain(from);
            }

            // 2. 检查是否为空投领取
            checkAndNotifyAirdrop(to, value, timestamp);

            // 3. ✅ 发送实时通知给用户
            sendTransferNotification(to, from, value, txHash, timestamp);

        } catch (Exception e) {
            log.error("Failed to process transfer event", e);
        }
    }

    /**
     * 处理空投领取事件 (Merkle Airdrop)
     */
    private void handleAirdropEvent(Map<String, Object> event) {
        try {
            String recipient = (String) event.get("recipient");
            String amountStr = (String) event.get("amount");
            String txHash = (String) event.get("txHash");

            log.info("Processing AirdropClaimed: recipient={}, amount={}", recipient, amountStr);

            CompletableFuture.runAsync(() -> {
                try {
                    // 找到当前活跃的配置
                    LambdaQueryWrapper<AirdropConfigEntity> configWrapper = new LambdaQueryWrapper<>();
                    configWrapper.eq(AirdropConfigEntity::getStatus, 1).last("LIMIT 1");
                    AirdropConfigEntity config = airdropConfigMapper.selectOne(configWrapper);

                    if (config != null) {
                        // 更新白名单状态
                        LambdaQueryWrapper<AirdropWhitelistEntity> whiteWrapper = new LambdaQueryWrapper<>();
                        whiteWrapper.eq(AirdropWhitelistEntity::getConfigId, config.getId())
                                .eq(AirdropWhitelistEntity::getAddress, recipient);

                        AirdropWhitelistEntity record = airdropWhitelistMapper.selectOne(whiteWrapper);
                        if (record != null && !record.getHasClaimed()) {
                            record.setHasClaimed(true);
                            record.setClaimTxHash(txHash);
                            record.setClaimTime(LocalDateTime.now());
                            airdropWhitelistMapper.updateById(record);

                            // 发送通知
                            notificationService.sendSystemNotification(recipient, "空投领取成功",
                                    String.format("您已成功领取 %s 个 EXTH 代币。", amountStr));
                        }
                    }

                } catch (Exception e) {
                    log.error("Failed to sync airdrop claim", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling AirdropClaimed event", e);
        }
    }

    /**
     * 处理交易匹配事件（创建交易对）
     */
    private void handleTradeMatchedEvent(Map<String, Object> event) {
        try {
            String tradeIdStr = (String) event.get("tradeId");
            BigInteger tradeId = new BigInteger(tradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeCreated event: tradeId={}, txHash={}", tradeId, txHash);

            CompletableFuture.runAsync(() -> {
                try {
                    // 从链上拉取该交易的完整信息
                    ExchangeWrapper.TradeInfo info = exchangeContract.getTradeInfo(tradeId);

                    if (info != null) {
                        // 构建数据库实体
                        TradeRecordEntity trade = new TradeRecordEntity();
                        //trade.setTradeId(tradeId.toString()); // 业务 ID
                        trade.setChainTradeId(Long.valueOf(tradeId.toString()));       // 链上索引 ID
                        trade.setPartyA(info.partyA);
                        trade.setPartyB(info.partyB);
                        trade.setAmount(new BigDecimal(info.amount));

                        trade.setExthReward(new BigDecimal(info.exthReward));
                        trade.setFeeAmount(new BigDecimal(info.feeAmount));
                        trade.setStatus(1); // 1-已匹配/已创建

                        // 转换链上时间戳 (BigInteger) 为 Java 时间对象 (LocalDateTime)
                        long createTimestamp = info.createTime.longValue();
                        LocalDateTime createDateTime = LocalDateTime.ofEpochSecond(createTimestamp, 0, java.time.ZoneOffset.UTC);
                        LocalDateTime expireDateTime = createDateTime.plusHours(TRADE_TIMEOUT_HOURS);

                        trade.setCreateTime(createDateTime);
                        trade.setExpireTime(expireDateTime);

                        // 插入或更新数据库
                        TradeRecordEntity existing = tradeMapper.selectByTradeId(tradeIdStr);
                        if (existing == null) {
                            tradeMapper.insert(trade);
                            log.info("New trade {} synced to DB from chain.", tradeId);
                        } else {
                            tradeMapper.updateById(existing);
                        }

                        // 发送通知给交易双方（使用统一通知服务）
                        if (info.partyA != null && !info.partyA.isEmpty()) {
                            notificationService.sendTradeNotification(info.partyA, tradeIdStr, "created");
                        }
                        if (info.partyB != null && !info.partyB.isEmpty()) {
                            notificationService.sendTradeNotification(info.partyB, tradeIdStr, "created");
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to sync trade creation for ID: {}", tradeId, e);
                }
            });

        } catch (Exception e) {
            log.error("Error processing TradeCreated event", e);
        }
    }

    /**
     * 处理交易完成事件
     * 关键改进：当监听到 TradeCompleted 事件时，更新所有相关状态
     */
    private void handleTradeCompletedEvent(Map<String, Object> event) {
        try {
            String tradeIdStr = (String) event.get("tradeId");
            BigInteger tradeId = new BigInteger(tradeIdStr);
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeCompleted event: tradeId={}", tradeId);

            // 异步同步交易数据到数据库并发送通知
            CompletableFuture.runAsync(() -> {
                try {
                    // 从链上获取交易完整信息
                    ExchangeWrapper.TradeInfo tradeInfo = exchangeContract.getTradeInfo(tradeId);

                    if (tradeInfo != null) {
                        // 查找链下对应的交易记录（使用链上 ID 查询）
                        TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeIdStr);

                        if (trade != null) {
                            // 同步关键状态字段
                            trade.setStatus(tradeInfo.state.intValue()); // 更新为链上真实状态
                            trade.setChainTxHash(txHash);

                            // 转换链上完成时间戳
                            if (tradeInfo.completeTime != null && tradeInfo.completeTime.longValue() > 0) {
                                trade.setCompleteTime(
                                        LocalDateTime.ofEpochSecond(tradeInfo.completeTime.longValue(), 0, ZoneOffset.UTC)
                                );
                            }

                            // 更新数据库
                            tradeMapper.updateById(trade);
                            log.info("Trade {} marked as completed in DB.", tradeIdStr);

                            // 发送通知给交易双方
                            if (trade.getPartyA() != null && !trade.getPartyA().isEmpty()) {
                                notificationService.sendTradeNotification(trade.getPartyA(), tradeIdStr, "completed");
                            }
                            if (trade.getPartyB() != null && !trade.getPartyB().isEmpty()) {
                                notificationService.sendTradeNotification(trade.getPartyB(), tradeIdStr, "completed");
                            }
                        } else {
                            log.warn("️ Trade record not found in DB for chain ID: {}", tradeIdStr);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to sync TradeCompleted info from chain", e);
                }
            });

        } catch (Exception e) {
            log.error("Error processing TradeCompleted event", e);
        }
    }



    /**
     * 处理 MultiSigWallet 委员会成员添加事件
     */
    private void handleCommitteeMemberAddedEvent(Map<String, Object> event) {
        try {
            String memberAddress = (String) event.get("member");
            log.info("Processing CommitteeMemberAdded: {}", memberAddress);

            CompletableFuture.runAsync(() -> {
                try {
                    LambdaQueryWrapper<CommitteeMemberEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CommitteeMemberEntity::getAddress, memberAddress);
                    CommitteeMemberEntity existing = committeeMemberMapper.selectOne(wrapper);

                    if (existing == null) {
                        CommitteeMemberEntity member = new CommitteeMemberEntity();
                        member.setAddress(memberAddress);
                        member.setIsActive(true);
                        member.setJoinTime(LocalDateTime.now());
                        member.setUpdateTime(LocalDateTime.now());
                        committeeMemberMapper.insert(member);

                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您已被任命为仲裁委员会成员",
                                "committee_appointed"
                        );

                        log.info(" Committee member added: {}", memberAddress);
                    } else {
                        existing.setIsActive(true);
                        existing.setUpdateTime(LocalDateTime.now());
                        committeeMemberMapper.updateById(existing);

                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您的仲裁委员会成员资格已恢复",
                                "committee_reactivated"
                        );

                        log.info(" Committee member reactivated: {}", memberAddress);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync committee member added", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling CommitteeMemberAdded event", e);
        }
    }

    /**
     * 处理 MultiSigWallet 委员会成员移除事件
     */
    private void handleCommitteeMemberRemovedEvent(Map<String, Object> event) {
        try {
            String memberAddress = (String) event.get("member");
            log.info("Processing CommitteeMemberRemoved: {}", memberAddress);

            CompletableFuture.runAsync(() -> {
                try {
                    LambdaQueryWrapper<CommitteeMemberEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CommitteeMemberEntity::getAddress, memberAddress);
                    CommitteeMemberEntity existing = committeeMemberMapper.selectOne(wrapper);

                    if (existing != null) {
                        existing.setIsActive(false);
                        existing.setLeaveTime(LocalDateTime.now());
                        existing.setUpdateTime(LocalDateTime.now());
                        committeeMemberMapper.updateById(existing);

                        notificationService.sendSystemNotification(
                                memberAddress,
                                "您的仲裁委员会成员资格已被移除",
                                "committee_removed"
                        );

                        log.info(" Committee member deactivated: {}", memberAddress);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync committee member removed", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling CommitteeMemberRemoved event", e);
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

            CompletableFuture.runAsync(() -> {
                try {
                    daoService.syncProposalFromChain(proposalId, proposer, txHash);

                    notificationService.sendDaoProposalNotification(
                            proposer,
                            proposalId,
                            "新提案 #" + proposalId,
                            "created"
                    );

                    log.info("DAO proposal {} synced and notified", proposalId);

                } catch (Exception e) {
                    log.error("Failed to sync DAO proposal created", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling DAO ProposalCreated event", e);
        }
    }

    /**
     * 处理 DAO 投票事件
     */
    private void handleDaoVoteCastEvent(Map<String, Object> event) {
        try {
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);
            String voter = (String) event.get("voter");
            boolean support = Boolean.TRUE.equals(event.get("support"));


            log.info("Processing DAO VoteCast: proposalId={}", proposalId);

            CompletableFuture.runAsync(() -> {
                try {
                    daoService.syncProposalVotesFromChain(proposalId);

                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                    ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                    if (record != null) {
                        String action = support ? "voted_support" : "voted_against";
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
                    }
                } catch (Exception e) {
                    log.error("Failed to sync DAO vote", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling DAO VoteCast event", e);
        }
    }

    /**
     * 处理 DAO 提案入列（进入公示期）事件
     */
    private void handleDaoProposalQueuedEvent(Map<String, Object> event) {
        try {
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);

            log.info("Processing DAO ProposalQueued: id={}", proposalId);

            CompletableFuture.runAsync(() -> {
                try {
                    // 更新数据库状态为 4 (Queued)
                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                    ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                    if (record != null) {
                        // 从链上获取 eta (执行时间)
                        DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);
                        if (info.eta != null) {
                            record.setDeadline(info.eta.longValue());
                        }
                        record.setStatus(info.status);
                        proposalRecordMapper.updateById(record);
                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 进入公示期",
                                "proposal_queued"
                        );

                        log.info(" Proposal {} status updated to Queued and notified", proposalId);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync DAO proposal queued", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling DAO ProposalQueued event", e);
        }
    }

    /**
     * 处理 DAO 提案执行事件
     */
    private void handleDaoProposalExecutedEvent(Map<String, Object> event) {
        try {
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);

            log.info("Processing DAO ProposalExecuted: id={}", proposalId);

            CompletableFuture.runAsync(() -> {
                try {
                    DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);

                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                    ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                    if (record != null) {
                        record.setStatus(info.status); // Executed
                        proposalRecordMapper.updateById(record);

                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 已执行",
                                "proposal_executed"
                        );
                        log.info(" Proposal {} marked as executed", proposalId);

                        // 检查是否为空投提案并初始化配置
                        initAirdropConfigIfMatched(proposalIdStr, record.getDescription());

                    }
                } catch (Exception e) {
                    log.error("Failed to sync DAO proposal executed", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling DAO ProposalExecuted event", e);
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

                // 创建配置记录
                AirdropConfigEntity config = new AirdropConfigEntity();
                config.setProposalId(proposalId);
                config.setMerkleRoot(merkleRoot);
                config.setStatus(1); // 1-进行中
                config.setTokenAddress(exthContractAddress);
                config.setTotalAmount(java.math.BigDecimal.ZERO); // 初始为0，后续根据链上余额更新
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

    /**
     * 从 JSON 文件加载 Merkle Root
     */
    private String loadMerkleRootFromJson() throws Exception {
        // 路径根据实际部署位置调整，这里指向 exc-contracts 目录下的输出文件
        String filePath = "D:/Users/asus/Desktop/区块链项目/exchange-system/exc-contracts/merkle-output.json";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        Map<String, Object> map = new ObjectMapper().readValue(content, new TypeReference<Map<String, Object>>() {});
        return (String) map.get("merkleRoot");
    }

    /**
     * 将 whitelist.json 导入数据库
     */
    private void importWhitelistToDb(Long configId) throws Exception {
        String filePath = "D:/Users/asus/Desktop/区块链项目/exchange-system/exc-contracts/whitelist.json";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        // 解析 JSON: {"address": "amount", ...}
        Map<String, String> whitelistMap = new ObjectMapper().readValue(content, new TypeReference<Map<String, String>>() {});

        List<AirdropWhitelistEntity> list = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : whitelistMap.entrySet()) {
            AirdropWhitelistEntity entity = new AirdropWhitelistEntity();
            entity.setConfigId(configId);
            entity.setAddress(entry.getKey());
            // JSON 里是带小数位的字符串，如果是整数需根据合约 decimals 转换
            entity.setAmount(new java.math.BigDecimal(entry.getValue()).divide(java.math.BigDecimal.TEN.pow(6)));
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
            String proposalIdStr = String.valueOf(event.get("proposalId"));
            BigInteger proposalId = new BigInteger(proposalIdStr);

            log.info("Processing DAO ProposalCanceled: id={}", proposalId);

            CompletableFuture.runAsync(() -> {
                try {
                    DaoWrapper.ProposalInfo info = daoWrapper.getProposal(proposalId);

                    LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                    ProposalRecordEntity record = proposalRecordMapper.selectOne(wrapper);

                    if (record != null) {
                        record.setStatus(info.status); // Canceled
                        proposalRecordMapper.updateById(record);

                        notificationService.sendDaoProposalNotification(
                                record.getProposer(),
                                proposalIdStr,
                                "提案 #" + proposalId + " 已取消",
                                "proposal_canceled"
                        );

                        log.info("Proposal {} marked as canceled", proposalId);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync DAO proposal canceled", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling DAO ProposalCanceled event", e);
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

            CompletableFuture.runAsync(() -> {
                UserEntity userEntity = userMapper.selectByAddress(user);
                if (userEntity != null && !userEntity.getIsBlacklisted()) {
                    userEntity.setIsBlacklisted(true);
                    userEntity.setUpdateTime(LocalDateTime.now());
                    userMapper.updateById(userEntity);

                    notificationService.sendSystemNotification(user, "账户受限通知",
                            "由于您的账户存在违规行为，已被列入系统黑名单。如有疑问请联系仲裁委员会。");
                    log.info("User {} blacklisted in DB and notified.", user);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process user blacklist event", e);
        }
    }

    /**
     * 处理手续费收取事件
     */
    private void handleFeeCollectedEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String payerA = (String) event.get("feePayerA");
            String payerB = (String) event.get("feePayerB");
            String feeAmount = String.valueOf(event.get("feeAmount"));

            log.info("Processing FeeCollected: tradeId={}, payers={}/{}", tradeId, payerA, payerB);

            // 异步记录财务流水或更新交易表的实收手续费字段
            CompletableFuture.runAsync(() -> {
                TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                if (trade != null) {
                    // 可以在这里记录具体的扣费日志到 t_fee_log 表
                    log.info("Fee of {} EXTH collected for trade {}", feeAmount, tradeId);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process fee collected event", e);
        }
    }

    /**
     * 处理赔偿支付事件 (由 Treasure 合约触发)
     */
    private void handleCompensationPaidEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String recipient = (String) event.get("recipient");
            String amount = String.valueOf(event.get("amount"));
            String txHash = (String) event.get("txHash");

            log.info("Processing CompensationPaid: tradeId={}, recipient={}, amount={}", tradeId, recipient, amount);

            CompletableFuture.runAsync(() -> {
                // 更新争议记录的最终执行状态
                DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                        new QueryWrapper<DisputeRecordEntity>().eq("trade_id", tradeId)
                );

                if (dispute != null) {
                    dispute.setProposalStatus(3); // 假设 3 代表已执行赔偿
                    disputeRecordMapper.updateById(dispute);
                }

                // 2. 通知收款方
                notificationService.sendSystemNotification(recipient, "仲裁赔偿金到账",
                        String.format("关于订单 %s 的仲裁赔偿款 %s USDT 已打入您的账户。交易哈希: %s",
                                tradeId, amount, txHash));

                // 再次通知双方争议彻底终结
                TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                if (trade != null) {
                    notificationService.sendArbitrationNotification(trade.getPartyA(), tradeId, "party_a", "compensation_paid");
                    notificationService.sendArbitrationNotification(trade.getPartyB(), tradeId, "party_b", "compensation_paid");
                }
            });
        } catch (Exception e) {
            log.error("Failed to process compensation paid event", e);
        }
    }


    /**
     * 监听链上争议解决事件
     * 核心原则：状态由链上事件驱动，数据库仅做持久化记录
     */
    public void handleDisputeResolvedEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String txHash = (String) event.get("txHash");
            log.info("Received DisputeResolved event from chain for trade: {}", tradeId);

            // 使用异步处理，避免阻塞事件监听器
            CompletableFuture.runAsync(() -> {
                try {
                    // 1. 从链上获取最终状态和详细信息
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(new BigInteger(tradeId));

                    // 2. 根据链上数据更新数据库
                    TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                    if (trade != null) {
                        // 直接使用链上返回的真实状态 (state 6 = Resolved)
                        trade.setStatus(chainInfo.state.intValue());

                        // 同步争议相关字段
                        trade.setDisputeStatus(RESOLVED); // 2-已解决

                        // 使用链上的完成时间戳
                        if (chainInfo.completeTime != null && chainInfo.completeTime.longValue() > 0) {
                            trade.setCompleteTime(
                                    LocalDateTime.ofEpochSecond(chainInfo.completeTime.longValue(), 0, ZoneOffset.UTC)
                            );
                        }
                        trade.setChainTxHash(txHash);

                        tradeMapper.updateById(trade);
                        log.info("Trade {} status synced from chain: state={}, disputedParty={}",
                                tradeId, chainInfo.state, chainInfo.disputedParty);

                        // 分别通知交易双方
                        notificationService.sendArbitrationNotification(trade.getPartyA(), tradeId, "party_a", "resolved");
                        notificationService.sendArbitrationNotification(trade.getPartyB(), tradeId, "party_b", "resolved");
                    }
                } catch (Exception e) {
                    log.error("Failed to handle DisputeResolved event for trade {}", tradeId, e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to process DisputeResolved event", e);
        }
    }

    /**
     * 处理仲裁提案创建事件
     */
    private void handleArbitrationProposalCreatedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String tradeId = String.valueOf(event.get("tradeId"));
            String accusedAddress = String.valueOf(event.get("accused"));
            String victimAddress = String.valueOf(event.get("victim"));
            String compensationAmount = String.valueOf(event.get("compensationAmount"));
            String txHash = String.valueOf(event.get("txHash"));

            log.info("Processing ArbitrationProposalCreated: proposalId={}, tradeId={}, accused={}",
                    proposalId, tradeId, accusedAddress);

            // 异步同步数据并发送通知
            CompletableFuture.runAsync(() -> {
                try {
                    // 查找并更新争议记录
                    DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                            new QueryWrapper<com.mnnu.entity.DisputeRecordEntity>()
                                    .eq("trade_id", tradeId)
                    );

                    if (dispute != null) {
                        dispute.setProposalId(proposalId);
                        dispute.setProposalTxHash(txHash);

                        dispute.setCompensationAmount(new BigDecimal(compensationAmount));


                        // 从链上获取详细的提案信息（包括 deadline）
                        try {
                            DisputeDTO chainDetails = multiSigWalletService.getProposalDetails(new BigInteger(proposalId));
                            if (chainDetails != null && chainDetails.getDeadline() != null) {
                                dispute.setDeadline(
                                        chainDetails.getDeadline());
                            }
                            // 同步初始票数
                            dispute.setVoteCount(chainDetails.getVoteCount());
                            dispute.setRejectCount(chainDetails.getRejectCount());
                        } catch (Exception e) {
                            log.warn("Failed to fetch proposal details from chain for ID: {}", proposalId, e);
                        }

                        disputeRecordMapper.updateById(dispute);
                        log.info("Dispute record {} linked to proposal {}", dispute.getId(), proposalId);
                    } else {
                        log.warn("No dispute record found for tradeId: {}", tradeId);
                    }

                    // 发送通知给被指控方 (Accused)
                    notificationService.sendSystemNotification(accusedAddress, "仲裁通知",
                            String.format("您有一笔关于订单 %s 的仲裁提案已提交至仲裁委员会。提案ID: %s，涉及补偿金额: %s USDT。",
                                    tradeId, proposalId, compensationAmount));

                    // 发送通知给申诉方 (Victim)
                    notificationService.sendSystemNotification(victimAddress, "仲裁进度",
                            String.format("您发起的关于订单 %s 的仲裁已进入审理阶段。提案ID: %s。", tradeId, proposalId));

                    // 发送通知给所有仲裁委员会成员
                    List<CommitteeMemberEntity> members = committeeMemberMapper.selectList(
                            new QueryWrapper<CommitteeMemberEntity>().eq("is_active", 1)
                    );

                    for (CommitteeMemberEntity member : members) {
                        notificationService.sendSystemNotification(member.getAddress(), "待处理仲裁任务",
                                String.format("新的仲裁提案 %s 已创建，涉及订单 %s。请登录后台查看详细信息并进行投票。",
                                        proposalId, tradeId));
                    }

                } catch (Exception e) {
                    log.error("Failed to sync arbitration proposal created", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalCreated event", e);
        }
    }

    /**
     * 处理仲裁投票事件
     */
    private void handleArbitrationProposalVotedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            String voter = (String) event.get("voter");
            boolean support = Boolean.TRUE.equals(event.get("support"));


            log.info("Processing ArbitrationProposalVoted: proposalId={}", proposalId);

            CompletableFuture.runAsync(() -> {
                try {
                    // 从链上获取最新的票数和状态
                    DisputeDTO details = multiSigWalletService.getProposalDetails(new BigInteger(proposalId));

                    // 更新数据库
                    DisputeRecordEntity dispute = disputeRecordMapper.selectOne(
                            new QueryWrapper<DisputeRecordEntity>()
                                    .eq("proposal_id", proposalId)
                    );

                    if (dispute != null) {
                        dispute.setVoteCount(details.getVoteCount());
                        dispute.setRejectCount(details.getRejectCount());
                        dispute.setProposalStatus(details.getStatus()); // 状态可能随投票变为 Executed
                        disputeRecordMapper.updateById(dispute);
                    }

                    // 3. 发送通知给争议双方
                    String voteAction = support ? "支持" : "反对";
                    String content = String.format("仲裁委员会成员 %s 已对您的仲裁提案 %s 投出 %s 票。当前票数 - 赞成: %d, 反对: %d。",
                            voter, proposalId, voteAction, details.getVoteCount(), details.getRejectCount());

                    // 通知申诉方 (Initiator/Victim)
                    if (dispute.getInitiator() != null) {
                        notificationService.sendSystemNotification(dispute.getInitiator(), "仲裁投票进度", content);
                    }

                    // 通知被指控方 (Accused)
                    if (dispute.getAccused() != null) {
                        notificationService.sendSystemNotification(dispute.getAccused(), "仲裁投票进度", content);
                    }

                } catch (Exception e) {
                    log.error("Failed to sync arbitration vote", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalVoted event", e);
        }
    }

    /**
     * 处理仲裁提案终结/执行事件
     */
    private void handleArbitrationProposalFinalizedEvent(Map<String, Object> event) {
        try {
            String proposalId = String.valueOf(event.get("proposalId"));
            log.info("Processing ArbitrationProposalFinalized: proposalId={}", proposalId);

            // 逻辑与投票类似，主要是更新最终状态
            handleArbitrationProposalVotedEvent(event);
        } catch (Exception e) {
            log.error("Error handling ArbitrationProposalFinalized event", e);
        }
    }


    /**
     * 处理奖励发放事件
     */
    private void handleRewardEvent(Map<String, Object> event) {
        try {
            String user = (String) event.get("user");
            BigInteger amount = new BigInteger((String) event.get("amount"));
            String tradeId = (String) event.get("tradeId");

            log.info("Processing RewardDistributed: user={}, amount={}, tradeId={}", user, amount, tradeId);

            CompletableFuture.runAsync(() -> {
                // 1. 更新链上余额
                userService.updateExthBalanceOnChain(user);

                // 2. 发送通知
                notificationService.sendSystemNotification(user, "交易奖励到账",
                        String.format("您在订单 %s 中成功获得 %s EXTH 奖励。", tradeId, amount.toString()));
            });

        } catch (Exception e) {
            log.error("Failed to process reward event", e);
        }
    }

    /**
     * 发送黑名单通知给用户
     */
    private void sendBlacklistNotification(String user, Number timestamp) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress(user);
            notification.setTitle("用户黑名单通知");
            notification.setContent("您已被列入黑名单");
            notification.setType(2);
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            notificationService.sendNotification(notification);
        } catch (Exception e) {
            log.error("Failed to send blacklist notification", e);
        }
    }

    /**
     * 发送黑名单通知给管理员
     */
    private void notifyAdminAboutBlacklist(String user, String txHash, Number blockNumber, Number timestamp) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress("admin"); // 管理员标识
            notification.setTitle("管理员通知 - 用户拉黑");
            notification.setContent(String.format("用户 %s 被合约拉黑，交易哈希：%s", user, txHash));
            notification.setType(2);
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            notificationService.sendNotification(notification);
        } catch (Exception e) {
            log.error("Failed to notify admin about blacklist", e);
        }
    }


    /**
     * 发送到匹配引擎
     */
    private void sendToMatchingEngine(String tradeId, String partyA, String partyB, BigInteger amount) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("tradeId", tradeId);
            message.put("partyA", partyA);
            message.put("partyB", partyB);
            message.put("amount", amount.toString());
            message.put("eventType", "TRADE_MATCHED");
            message.put("timestamp", System.currentTimeMillis());

            // 这里不实际发送，TradeMatchListener 已经监听 TRADE_MATCH 队列
            // 如果需要发送，需要注入 RabbitTemplate
            log.debug("Would send to matching engine: tradeId={}", tradeId);
        } catch (Exception e) {
            log.error("Failed to send to matching engine", e);
        }
    }



    /**
     * 更新用户交易统计
     */
    private void updateUserTradeStats(String partyA, String partyB) {
        try {
            // 增加交易次数
            userService.incrementTradeCount(partyA);
            userService.incrementTradeCount(partyB);

            log.debug("User trade stats updated for partyA={}, partyB={}", partyA, partyB);
        } catch (Exception e) {
            log.error("Failed to update user trade stats", e);
        }
    }

    /**
     * 发送完成通知
     */
    private void sendCompletionNotification(String partyA, String partyB, String tradeId, Number timestamp) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "trade_completed");
            notification.put("tradeId", tradeId);
            notification.put("timestamp", System.currentTimeMillis());

            // 发送 WebSocket 通知（添加 null 检查）
            String message = objectMapper.writeValueAsString(notification);

            if (partyA != null && !partyA.isEmpty()) {
                CustomWebSocketHandler.sendToUser(partyA, message);
                log.debug("Completion notification sent to partyA: {}", partyA);
            } else {
                log.warn("PartyA address is null or empty, cannot send WebSocket notification for trade: {}", tradeId);
            }

            if (partyB != null && !partyB.isEmpty()) {
                CustomWebSocketHandler.sendToUser(partyB, message);
                log.debug("Completion notification sent to partyB: {}", partyB);
            } else {
                log.warn("PartyB address is null or empty, cannot send WebSocket notification for trade: {}", tradeId);
            }

            log.debug("Completion notification processing completed for trade {}", tradeId);
        } catch (Exception e) {
            log.error("Failed to send completion notification", e);
        }
    }


    /**
     * 检查并通知空投领取
     */
    private void checkAndNotifyAirdrop(String to, BigInteger value, Number timestamp) {
        try {
            // 这个逻辑已经在 handleTransferEvent 中处理了
            // 如果是空投转账，会在 handleAirdropEvent 中统一处理
            log.debug("Check airdrop for user: {}, amount: {}", to, value);
        } catch (Exception e) {
            log.warn("Failed to check airdrop: {}", e.getMessage());
        }
    }

    /**
     * 发送转账通知
     * 改进：使用统一的通知服务（自动判断在线/离线）
     */
    private void sendTransferNotification(String to, String from, BigInteger value,
                                          String txHash, Number timestamp) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress(to);
            notification.setTitle("转账到账通知");
            notification.setContent(String.format("您收到来自 %s 的 %s 个 EXTH 转账，交易哈希：%s",
                    from, value.toString(), txHash));
            notification.setType(0);
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            // ✅ 使用统一的通知服务（会自动判断在线/离线）
            notificationService.sendNotification(notification);

            log.debug("Transfer notification sent to user {}", to);
        } catch (Exception e) {
            log.warn("Failed to send transfer notification: {}", e.getMessage());
        }
    }

    /**
     * 发送交易匹配 WebSocket 通知
     */
    private void sendTradeMatchedWebSocket(String partyA, String partyB, String tradeId, BigInteger amount) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "trade_matched");
            notification.put("tradeId", tradeId);
            notification.put("amount", amount.toString());
            notification.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(notification);

            if (partyA != null && !partyA.isEmpty()) {
                CustomWebSocketHandler.sendToUser(partyA, message);
                log.debug("Trade matched notification sent to partyA: {}", partyA);
            } else {
                log.warn("PartyA address is null or empty, cannot send WebSocket notification");
            }

            if (partyB != null && !partyB.isEmpty()) {
                CustomWebSocketHandler.sendToUser(partyB, message);
                log.debug("Trade matched notification sent to partyB: {}", partyB);
            } else {
                log.warn("PartyB address is null or empty, cannot send WebSocket notification");
            }

            log.info("Trade matched WebSocket notifications sent successfully for trade: {}", tradeId);
        } catch (Exception e) {
            log.error("Failed to send trade matched WebSocket notification for trade {}: {}", tradeId, e.getMessage());
        }
    }
    /**
     * 处理用户类型升级事件
     */
    private void handleUserUpgradedEvent(Map<String, Object> event) {
        try {
            String userAddress = (String) event.get("user");
            Object typeObj = event.get("newType");
            int newUserType = typeObj instanceof Number ? ((Number) typeObj).intValue() : Integer.parseInt(typeObj.toString());

            log.info("Processing UserUpgraded: user={}, newType={}", userAddress, newUserType);

            CompletableFuture.runAsync(() -> {
                try {
                    // 查找或创建用户
                    UserEntity user = userMapper.selectByAddress(userAddress);
                    if (user == null) {
                        user = new UserEntity();
                        user.setAddress(userAddress);
                        user.setRegisterTime(LocalDateTime.now());
                    }

                    int oldType = user.getUserType() != null ? user.getUserType() : -1;

                    // 直接同步链上状态到数据库
                    user.setUserType(newUserType);
                    user.setLastActiveTime(LocalDateTime.now());
                    user.setUpdateTime(LocalDateTime.now());

                    // 根据同步后的最终状态，修正关联字段
                    if (newUserType == 0) {
                        // 场景 A: 新用户 (NEW)
                        // 链上 registerUser 默认给 3 次机会
                        user.setNewUserTradeCount(3);

                    } else {
                        // 场景 B & C: 普通用户(NORMAL) 或 种子用户(SEED)
                        // 只要不是 NEW，链上的 newUserTradeCount 必然为 0
                        user.setNewUserTradeCount(0);
                    }

                    // 保存更新
                    if (user.getId() == null) {
                        userMapper.insert(user);
                    } else {
                        userMapper.updateById(user);
                    }

                    notificationService.sendUserUpgradeNotification(userAddress, newUserType);


                    log.info(" User {} synced from chain: type {} -> {}", userAddress, oldType, newUserType);

                } catch (Exception e) {
                    log.error("Failed to process UserUpgraded event", e);
                }
            });
        } catch (Exception e) {
            log.error("Error handling UserUpgraded event", e);
        }
    }


    /**
     * 处理甲方确认事件
     */
    private void handlePartyAConfirmedEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String txHash = (String) event.get("txHash");

            log.info("Processing PartyAConfirmed event: tradeId={}, txHash={}", tradeId, txHash);

            // 从链上查询最新状态
            CompletableFuture.runAsync(() -> {
                try {
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(new BigInteger(tradeId));

                    TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                    if (trade != null) {
                        // 使用链上返回的真实状态值
                        trade.setStatus(chainInfo.state.intValue());
                        trade.setPartyAConfirmTime(LocalDateTime.now());
                        tradeMapper.updateById(trade);

                        log.info("Trade {} status synced from chain: state={}", tradeId, chainInfo.state);
                    }

                    // 通知乙方
                    TradeRecordEntity updatedTrade = tradeMapper.selectByTradeId(tradeId);
                    if (updatedTrade != null && updatedTrade.getPartyB() != null) {
                        notificationService.sendTradeNotification(updatedTrade.getPartyB(), tradeId, "party_a_confirmed");
                    }
                } catch (Exception e) {
                    log.error("Failed to sync PartyAConfirmed state from chain", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process PartyAConfirmed event", e);
        }
    }


    /**
     * 处理乙方确认事件
     */
    private void handlePartyBConfirmedEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String txHash = (String) event.get("txHash");

            log.info("Processing PartyBConfirmed event: tradeId={}, txHash={}", tradeId, txHash);

            // 异步从链上查询最新状态
            CompletableFuture.runAsync(() -> {
                try {
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(new BigInteger(tradeId));

                    TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                    if (trade != null) {
                        // 直接使用链上返回的 state 值
                        trade.setStatus(chainInfo.state.intValue());
                        trade.setPartyBConfirmTime(LocalDateTime.now());
                        tradeMapper.updateById(trade);

                        log.info("Trade {} status synced from chain: state={}", tradeId, chainInfo.state);
                    }

                    // 通知甲方
                    TradeRecordEntity updatedTrade = tradeMapper.selectByTradeId(tradeId);
                    if (updatedTrade != null && updatedTrade.getPartyA() != null) {
                        notificationService.sendTradeNotification(updatedTrade.getPartyA(), tradeId, "party_b_confirmed");
                    }
                } catch (Exception e) {
                    log.error("Failed to sync PartyBConfirmed state from chain", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process PartyBConfirmed event", e);
        }
    }

    /**
     * 处理交易取消事件
     */
    private void handleTradeCancelledEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));

            log.info("Processing TradeCancelled event: tradeId={}", tradeId);

            // 异步从链上查询最新状态
            CompletableFuture.runAsync(() -> {
                try {
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(new BigInteger(tradeId));

                    TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                    if (trade != null) {
                        // 使用链上返回的状态值
                        trade.setStatus(chainInfo.state.intValue());
                        // 使用链上的完成时间戳
                        if (chainInfo.completeTime != null && chainInfo.completeTime.longValue() > 0) {
                            trade.setCompleteTime(
                                    LocalDateTime.ofEpochSecond(chainInfo.completeTime.longValue(), 0, ZoneOffset.UTC)
                            );
                        }
                        tradeMapper.updateById(trade);

                        log.info("Trade {} status synced from chain: state={}", tradeId, chainInfo.state);
                    }

                    // 通知双方
                    notificationService.sendTradeNotification(trade.getPartyA(), tradeId, "cancelled");
                    notificationService.sendTradeNotification(trade.getPartyB(), tradeId, "cancelled");


                } catch (Exception e) {
                    log.error("Failed to sync TradeCancelled state from chain", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process TradeCancelled event", e);
        }
    }

    /**
     * 处理交易争议事件
     */
    private void handleTradeDisputedEvent(Map<String, Object> event) {
        try {
            String tradeId = String.valueOf(event.get("tradeId"));
            String disputedParty = (String) event.get("disputedParty");
            String txHash = (String) event.get("txHash");

            log.info("Processing TradeDisputed event: tradeId={}, disputedParty={}", tradeId, disputedParty);

            // 异步从链上查询最新状态
            CompletableFuture.runAsync(() -> {
                try {
                    ExchangeWrapper.TradeInfo chainInfo = exchangeContract.getTradeInfo(new BigInteger(tradeId));

                    TradeRecordEntity trade = tradeMapper.selectByTradeId(tradeId);
                    if (trade != null) {
                        // 直接使用链上返回的状态值（ 5-Disputed）
                        trade.setStatus(chainInfo.state.intValue());
                        trade.setDisputedParty(chainInfo.disputedParty);
                        trade.setDisputeStatus(PENDING); // 处理中
                        tradeMapper.updateById(trade);

                        log.info(" Trade {} status synced from chain: state={}", tradeId, chainInfo.state);
                    }
                    DisputeRecordEntity dispute = disputeRecordMapper.selectById(tradeId);
                    dispute.setProposalId(tradeId);
                    dispute.setProposalTxHash(txHash);
                    dispute.setProposalStatus(PENDING);
                    dispute.setCreateTime(LocalDateTime.now());

                    // 通知双方
                    notificationService.sendTradeNotification(trade.getPartyA(), tradeId, "disputed");
                    notificationService.sendTradeNotification(trade.getPartyB(), tradeId, "disputed");
                } catch (Exception e) {
                    log.error("Failed to sync TradeDisputed state from chain", e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to process TradeDisputed event", e);
        }
    }



}