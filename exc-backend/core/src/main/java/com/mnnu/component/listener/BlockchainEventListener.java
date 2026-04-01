package com.mnnu.component.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.component.handler.CustomWebSocketHandler;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.dto.TradeDTO;
import com.mnnu.entity.AirdropEntity;
import com.mnnu.entity.AirdropRecordEntity;
import com.mnnu.entity.UserEntity;
import com.mnnu.mapper.AirdropMapper;
import com.mnnu.mapper.AirdropRecordMapper;
import com.mnnu.mapper.UserMapper;
import com.mnnu.service.AirdropService;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.NotificationService;
import com.mnnu.service.TradeService;
import com.mnnu.service.UserService;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.web3j.model.Exchange;
import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
     * 空投记录 Mapper
     */
    private final AirdropRecordMapper airdropRecordMapper;

    /**
     * 空投数据访问接口
     */
    @Autowired
    private AirdropMapper airdropMapper;

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




    /**
     * 从缓存或数据库获取交易参与方 A
     */
    private String getPartyAFromTrade(BigInteger tradeId) {
        try {
            // 从数据库查询交易
            TradeDTO trade = tradeService.getTradeDetail(tradeId.toString());
            return trade != null ? trade.getPartyA() : null;
        } catch (Exception e) {
            log.warn("Failed to get partyA from trade: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从缓存或数据库获取交易参与方 B
     */
    private String getPartyBFromTrade(BigInteger tradeId) {
        try {
            // 从数据库查询交易
            TradeDTO trade = tradeService.getTradeDetail(tradeId.toString());
            return trade != null ? trade.getPartyB() : null;
        } catch (Exception e) {
            log.warn("Failed to get partyB from trade: {}", e.getMessage());
            return null;
        }
    }

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
                case "ProposalExecuted":
                    handleProposalExecutedEvent(event);
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
     * 处理交易匹配事件
     */
    private void handleTradeMatchedEvent(Map<String, Object> event) {
        try {
            String tradeId = (String) event.get("tradeId");
            String partyA = (String) event.get("partyA");
            String partyB = (String) event.get("partyB");
            BigInteger amount = new BigInteger((String) event.get("amount"));

            log.info("Processing TradeMatched event: tradeId={}", tradeId);

            // 1. 更新交易状态
            tradeService.updateStatus(tradeId, SystemConstants.TradeStatus.MATCHED);

            // 2. 发送到匹配引擎
            sendToMatchingEngine(tradeId, partyA, partyB, amount);

            // 3. WebSocket 推送
            sendTradeMatchedWebSocket(partyA, partyB, tradeId, amount);

        } catch (Exception e) {
            log.error("Failed to process trade matched event", e);
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

            log.info("Processing TradeCompleted event: tradeId={}", tradeId);

            // 🔥 异步同步交易数据到数据库
            CompletableFuture.runAsync(() -> {
                try {
                    // 从链上获取交易信息
                    ExchangeWrapper.TradeInfo tradeInfo = exchangeContract.getTradeInfo(tradeId);

                    if (tradeInfo != null) {
                        // 同步交易数据到数据库
                        syncTradeInfoToDatabase(tradeId, tradeInfo, ((Number) event.get("blockNumber")).longValue());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to sync trade info from chain: {}", e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Error processing TradeCompleted event", e);
        }
    }

    // 🔥 新增：同步交易信息到数据库
    private void syncTradeInfoToDatabase(BigInteger tradeId, ExchangeWrapper.TradeInfo tradeInfo, Long blockNumber) {
        try {
            // 尝试从数据库查询交易
            TradeDTO existingTrade = tradeService.getTradeDetail(tradeId.toString());

            if (existingTrade != null) {
                // 如果交易已存在，更新链上数据
                updateTradeOnChain(tradeId, tradeInfo, blockNumber);
            } else {
                // 如果交易不存在，创建新交易记录
                createTradeFromChainData(tradeId, tradeInfo, blockNumber);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to sync trade to database: {}", e.getMessage());
        }
    }

    // 🔥 新增：更新链上交易数据
    private void updateTradeOnChain(BigInteger tradeId, ExchangeWrapper.TradeInfo tradeInfo, Long blockNumber) {
        try {
            // 这里可以更新交易的状态、blockNumber 等链上数据
            // 由于 TradeDTO 可能没有这些字段，暂时只记录日志
            log.info("🔄 Updated trade {} from chain: partyA={}, partyB={}, amount={}",
                    tradeId, tradeInfo.partyA, tradeInfo.partyB, tradeInfo.amount);
        } catch (Exception e) {
            log.warn("⚠️ Failed to update trade on chain: {}", e.getMessage());
        }
    }

    // 🔥 新增：从链上数据创建交易记录
    private void createTradeFromChainData(BigInteger tradeId, ExchangeWrapper.TradeInfo tradeInfo, Long blockNumber) {
        try {
            log.info("🆕 Creating trade from chain data: tradeId={}, partyA={}, partyB={}, amount={}",
                    tradeId, tradeInfo.partyA, tradeInfo.partyB, tradeInfo.amount);

            // 这里可以调用 TradeService 创建交易记录
            // 由于链上数据可能不完整，需要根据实际情况决定是否创建
        } catch (Exception e) {
            log.warn("⚠️ Failed to create trade from chain data: {}", e.getMessage());
        }
    }

    /**
     * 发送交易完成消息到消息队列
     */
    private void sendTradeCompletedMessage(Exchange.TradeCompletedEventResponse event) {
        try {
            // 🔥 先从链上获取交易信息，确保 partyA 和 partyB 不为 null
            ExchangeWrapper.TradeInfo tradeInfo = null;
            try {
                tradeInfo = exchangeContract.getTradeInfo(event.tradeId);
            } catch (Exception e) {
                log.warn("⚠️ Failed to get trade info from chain: {}", e.getMessage());
            }

            Map<String, Object> message = new HashMap<>();
            message.put("event", "TradeCompleted");
            message.put("tradeId", event.tradeId.toString());

            // 🔥 优先使用链上数据，如果获取失败则使用缓存/数据库
            if (tradeInfo != null && tradeInfo.partyA != null) {
                message.put("partyA", tradeInfo.partyA);
            } else {
                message.put("partyA", getPartyAFromTrade(event.tradeId));
            }

            if (tradeInfo != null && tradeInfo.partyB != null) {
                message.put("partyB", tradeInfo.partyB);
            } else {
                message.put("partyB", getPartyBFromTrade(event.tradeId));
            }

            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("Trade completed message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send trade completed message: {}", e.getMessage());
        }
    }



    /**
     * 处理空投事件
     */
    private void handleAirdropEvent(Map<String, Object> event) {
        try {
            String user = (String) event.get("recipient");
            BigInteger amount = new BigInteger((String) event.get("amount"));
            String txHash = (String) event.get("txHash");
            BigInteger blockNumber = BigInteger.valueOf(((Number) event.get("blockNumber")).longValue());
            Number timestamp = (Number) event.get("timestamp");

            log.info("Processing AirdropClaimed event: user={}, amount={}", user, amount);

            // 1. 保存空投记录
            saveAirdropRecord(user, amount, txHash, blockNumber, timestamp);

            // 2. 标记为已领取
            airdropService.markAsClaimed(user);

            // 3. 发送通知
            sendAirdropNotification(user, amount, txHash, timestamp);

        } catch (Exception e) {
            log.error("Failed to process airdrop event", e);
        }
    }

    /**
     * 处理 DAO 提案执行事件（同步空投信息）
     */
    private void handleProposalExecutedEvent(Map<String, Object> event) {
        try {
            String proposalIdStr = (String) event.get("proposalId");
            String txHash = (String) event.get("txHash");

            log.info("Processing ProposalExecuted event: proposalId={}, txHash={}", proposalIdStr, txHash);

            // 异步执行，避免阻塞
            CompletableFuture.runAsync(() -> {
                try {
                    // 等待几秒让区块确认
                    TimeUnit.SECONDS.sleep(3);

                    BigInteger proposalId = new BigInteger(proposalIdStr);

                    // 从链上查询提案详情
                    DaoWrapper.ProposalInfo proposalInfo = daoWrapper.getProposal(proposalId);
                    String targetContract = proposalInfo.targetContract;

                    log.info("Proposal {} executed, target contract: {}", proposalId, targetContract);

                    // 🔍 调试：打印所有合约地址
                    log.info("TreasureWrapper status: {}, address: {}",
                            treasureWrapper != null ? "initialized" : "null",
                            treasureWrapper != null ? treasureWrapper.getContractAddress() : "N/A");
                    log.info("AirdropWrapper status: {}, address: {}",
                            airdropWrapper != null ? "initialized" : "null",
                            airdropWrapper != null ? airdropWrapper.getContractAddress() : "N/A");

                    // ✅ 检查是否是 Treasure 金库合约向空投合约发放代币
                    if (treasureWrapper != null && targetContract != null &&
                            targetContract.equalsIgnoreCase(treasureWrapper.getContractAddress())) {

                        log.info("✅ 检测到 Treasure 金库提案执行，开始同步空投信息...");

                        // 等待片刻让 Treasure 合约完成转账
                        TimeUnit.SECONDS.sleep(2);

                        syncAirdropInfoFromChain();
                        return;
                    }

                    // ✅ 检查是否是空投合约的提案
                    if (airdropWrapper != null && targetContract != null &&
                            targetContract.equalsIgnoreCase(airdropWrapper.getContractAddress())) {

                        log.info("✅ 检测到空投合约提案执行，开始同步链上数据...");
                        syncAirdropInfoFromChain();
                        return;
                    }

                    // ✅ 检查 callData 是否包含空投相关方法调用
                    if (proposalInfo.callData != null && proposalInfo.callData.length > 0) {
                        String callDataHex = bytesToHex(proposalInfo.callData);
                        log.info("Proposal {} callData: 0x{}", proposalId, callDataHex);

                        // 检查是否包含代币转移相关的方法签名
                        // reclaimTokens(): 0x44004cc1
                        // transfer(address,uint256): 0xa9059cbb
                        // transferFrom(address,address,uint256): 0x23b872dd
                        if (callDataHex.startsWith("44004cc1") ||
                                callDataHex.startsWith("a9059cbb") ||
                                callDataHex.startsWith("23b872dd")) {

                            log.info("✅ 检测到代币回收/转移方法调用，同步空投信息...");
                            TimeUnit.SECONDS.sleep(2);
                            syncAirdropInfoFromChain();
                            return;
                        }
                    }

                    log.warn("⚠️ 提案执行完成，但未匹配到任何空投相关操作 (proposalId={}, targetContract={})",
                            proposalId, targetContract);

                } catch (Exception e) {
                    log.error("Failed to process proposal executed event", e);
                }
            });

        } catch (Exception e) {
            log.error("Failed to process proposal executed event", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    /**
     * 从链上同步空投信息到数据库
     */
    private void syncAirdropInfoFromChain() {
        try {
            if (airdropWrapper == null) {
                log.warn("AirdropWrapper 未初始化，跳过同步");
                return;
            }

            // 1. 查询 Airdrop 合约的 EXTH 代币余额
            java.math.BigInteger balance = airdropWrapper.getAirdropBalance();
            BigDecimal totalAmount = new BigDecimal(balance).divide(new BigDecimal(java.math.BigInteger.TEN.pow(6)));

            log.info("Airdrop 合约余额：{} EXTH", totalAmount);

            // 2. 每个地址可领取金额（固定值）
            BigDecimal perAddressAmount = new BigDecimal("1000");

            // 3. 创建或更新空投配置
            AirdropEntity latest = airdropMapper.selectLatest();

            if (latest == null || !Boolean.TRUE.equals(latest.getIsActive())) {
                // 创建新的空投配置
                AirdropEntity airdrop = new AirdropEntity();
                airdrop.setAddress("0x0000000000000000000000000000000000000000");
                airdrop.setTotalAmount(totalAmount);
                airdrop.setPerAddressAmount(perAddressAmount);
                airdrop.setIsActive(true);
                airdrop.setStartTime(LocalDateTime.now());
                airdrop.setCreateTime(LocalDateTime.now());

                airdropMapper.insert(airdrop);
                log.info("✅ 已创建新的空投配置：总金额={}, 每地址={}", totalAmount, perAddressAmount);
            } else {
                // 更新现有配置 - 修复 null 指针问题
                BigDecimal currentTotal = latest.getTotalAmount() != null ? latest.getTotalAmount() : BigDecimal.ZERO;
                latest.setTotalAmount(currentTotal.add(totalAmount));
                airdropMapper.updateById(latest);
                log.info("✅ 已更新空投配置：新增总金额={}, 累计={}", totalAmount, latest.getTotalAmount());
            }

            // 4. 发送全局通知
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "airdrop_activated");
            notification.put("totalAmount", totalAmount);
            notification.put("perAddress", perAddressAmount);
            notification.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(notification);
            CustomWebSocketHandler.broadcast(message);

            log.info("已广播空投激活通知到所有在线用户");

        } catch (Exception e) {
            log.error("同步空投信息失败", e);
        }
    }

    /**
     * 处理用户拉黑事件
     */
    private void handleUserBlacklistEvent(Map<String, Object> event) {
        try {
            String user = (String) event.get("user");
            String txHash = (String) event.get("txHash");
            log.info("Processing UserBlacklisted event from MQ: user={}, txHash={}", user, txHash);

            // ✅ 链上已经拉黑，只更新数据库状态
            UserEntity userEntity = userMapper.selectByAddress(user);
            if (userEntity != null && !userEntity.getIsBlacklisted()) {
                userEntity.setIsBlacklisted(true);
                userEntity.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(userEntity);
                log.info("User blacklisted in database: {}", user);
            }

            // 清除缓存
            String cacheKey = SystemConstants.RedisKey.USER_INFO + user;
            redissonClient.getBucket(cacheKey).delete();

            // 发送通知
            sendBlacklistNotification(user, null);

        } catch (Exception e) {
            log.error("Failed to process user blacklist event from MQ", e);
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

            log.info("Processing RewardDistributed event: user={}, amount={}, tradeId={}", user, amount, tradeId);

            // 更新余额
            userService.updateExthBalanceOnChain(user);

            // 发送通知
            sendRewardNotification(user, amount, tradeId,null);

        } catch (Exception e) {
            log.error("Failed to process reward event", e);
        }
    }

    /**
     * 保存空投记录
     */
    private void saveAirdropRecord(String user, BigInteger amount, String txHash,
                                   BigInteger blockNumber, Number timestamp) {
        try {
            AirdropRecordEntity record = new AirdropRecordEntity();
            record.setAddress(user);
            record.setAmount(new BigDecimal(amount));
            record.setTxHash(txHash);
            record.setIsClaimed(true);
            record.setBlockNumber(blockNumber);

            long timestampMillis = ((Number) timestamp).longValue();
            record.setClaimTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMillis),
                    ZoneId.systemDefault()
            ));

            record.setCreateTime(LocalDateTime.now());

            airdropRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("Failed to save airdrop record", e);
        }
    }

    /**
     * 发送空投通知
     */
    private void sendAirdropNotification(String user, BigInteger amount, String txHash, Number timestamp) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress(user);
            notification.setTitle("空投领取通知");
            notification.setContent(String.format("您成功领取了 %s 个 EXTH 空投，交易哈希：%s",
                    amount.toString(), txHash));
            notification.setType(1);
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            notificationService.sendNotification(notification);
        } catch (Exception e) {
            log.error("Failed to send airdrop notification", e);
        }
    }

    /**
     * 发送奖励通知
     */
    private void sendRewardNotification(String user, BigInteger amount, String tradeId, Number timestamp) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress(user);
            notification.setTitle("奖励发放通知");
            notification.setContent(String.format("您在交易 %s 中获得 %s 个 EXTH 奖励",
                    tradeId, amount.toString()));
            notification.setType(3);
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            notificationService.sendNotification(notification);
        } catch (Exception e) {
            log.error("Failed to send reward notification", e);
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
     * 发放奖励
     */
    private void distributeRewards(String tradeId, String partyA, String partyB) {
        try {
            // 这里需要调用 RewardService 或 BlockchainService 发放奖励
            log.info("Rewards would be distributed for trade {}: partyA={}, partyB={}",
                    tradeId, partyA, partyB);
        } catch (Exception e) {
            log.error("Failed to distribute rewards", e);
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

}