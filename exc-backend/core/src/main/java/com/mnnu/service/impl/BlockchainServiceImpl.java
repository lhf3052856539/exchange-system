package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.BlockchainTransactionDTO;
import com.mnnu.dto.TradeDTO;
import com.mnnu.entity.ProposalRecordEntity;
import com.mnnu.mapper.ProposalRecordMapper;
import com.mnnu.service.BlockchainService;
import com.mnnu.service.TradeService;
import com.mnnu.utils.Web3jUtil;
import com.mnnu.wrapper.*;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.model.Airdrop;
import org.web3j.model.Dao;
import org.web3j.model.EXTH;
import org.web3j.model.Exchange;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BlockchainServiceImpl implements BlockchainService {

    @Autowired
    private ExchangeWrapper exchangeContract;

    @Autowired
    private EXTHWrapper exthContract;

    @Autowired
    private AirdropWrapper airdropContract;

    @Autowired
    private DaoWrapper daoContract;

    @Autowired
    private TimelockWrapper timelockContract;

    @Autowired
    private USDTWrapper usdtContract;

    @Autowired
    private Web3j web3j;

    @Autowired
    private RabbitTemplate rabbitTemplate;



    @Autowired
    private RedisTemplate<String, String> redisTemplate;


    @Autowired
    private TradeService tradeService;


    @Autowired
    private TreasureWrapper treasureContract;

    @Autowired
    private ProposalRecordMapper proposalRecordMapper;


    // 缓存的奖励金额
    private BigInteger cachedRewardAmount = null;
    private long lastRewardFetchTime = 0;
    private static final long REWARD_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 分钟缓存



    // ============================================================
    // === Write Operations（链下 → 链上）：发送交易，修改链上状态
    // ============================================================

    /**
     * 在链上领取空投
     * 数据流向：链下请求 → 链上合约
     */
    @Override
    public String claimAirdropOnChain(String address, BigInteger amount, List<byte[]> merkleProof) {
        log.info("Claiming airdrop on chain for user: {}, amount: {}", address, amount);
        log.info("Merkle proof details:");
        log.info("  - Proof count: {}", merkleProof.size());
        for (int i = 0; i < merkleProof.size(); i++) {
            byte[] proof = merkleProof.get(i);
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : proof) {
                hexBuilder.append(String.format("%02x", b));
            }
            log.info("  - Proof[{}]: 0x{}", i, hexBuilder.toString());
        }

        try {
            if (airdropContract != null) {
                String txHash = airdropContract.claimAirdrop(amount, merkleProof);
                log.info("Airdrop claimed on chain successfully: txHash={}", txHash);
                return txHash;
            } else {
                log.warn("Airdrop contract not initialized");
                throw new RuntimeException("Airdrop contract not available");
            }
        } catch (Exception e) {
            log.error("Failed to claim airdrop on chain for {}: {}", address, e.getMessage());
            throw new RuntimeException("Failed to claim airdrop on chain: " + e.getMessage(), e);
        }
    }

    /**
     * 完成交易（调用 Exchange 合约）
     * 数据流向：链下请求 → 链上合约
     */
    public String completeTrade(BigInteger tradeId) {
        log.info("Completing trade on chain: tradeId={}", tradeId);
        try {
            return exchangeContract.completeTrade(tradeId);
        } catch (Exception e) {
            log.error("Failed to complete trade on chain for tradeId={}", tradeId, e);
            throw new RuntimeException("Complete trade failed: " + e.getMessage(), e);
        }
    }

    /**
     * 发送空投消息到消息队列
     */
    private void sendAirdropMessage(Airdrop.AirdropClaimedEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "AirdropClaimed");
            message.put("claimant", event.claimant);
            message.put("amount", event.amount.toString());
            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("Airdrop claimed message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send airdrop message: {}", e.getMessage());
        }
    }


    /**
     * 更新用户余额缓存（从链上同步到链下）
     * 数据流向：链上合约 → Redis 缓存
     */
    @Override
    public void updateExthBalanceOnChain(String address) {
        try {
            log.info("Updating EXTH balance from chain for user: {}", address);

            BigInteger onChainBalance = exthContract.balanceOf(address);

            String cacheKey = SystemConstants.RedisKey.USER_BALANCE + address;
            redisTemplate.opsForValue().set(cacheKey, onChainBalance.toString());

            log.debug("EXTH balance updated for {}: {}", address, onChainBalance);
        } catch (Exception e) {
            log.error("Failed to update EXTH balance for {}: {}", address, e.getMessage());
            throw new RuntimeException("Failed to update EXTH balance: " + e.getMessage(), e);
        }
    }


    // 保存订阅引用，用于后续取消订阅
    private final List<Disposable> disposables = new ArrayList<>();

    /**
     * 获取合约对象
     */
    @Override
    public Object getContract(String contractName) {
        String name = contractName.toLowerCase();
        if ("exchange".equals(name)) {
            return exchangeContract;
        } else if ("exth".equals(name)) {
            return exthContract;
        } else if ("airdrop".equals(name)) {
            return airdropContract;
        } else if ("dao".equals(name)) {
            return daoContract;
        } else if ("timelock".equals(name)) {
            return timelockContract;
        } else if ("usdt".equals(name)) {
            return usdtContract;
        } else {
            throw new IllegalArgumentException("Unknown contract: " + contractName);
        }
    }


    /**
     * 发送交易
     */
    @Override
    public String sendTransaction(String contractName, String methodName, Object... params) {
        log.info("Sending transaction to {} method {}", contractName, methodName);
        try {
            Object contract = getContract(contractName);

            if ("exchange".equals(contractName.toLowerCase())) {
                return handleExchangeTransaction(methodName, params);
            } else if ("exth".equals(contractName.toLowerCase())) {
                return handleExthTransaction(methodName, params);
            } else if ("airdrop".equals(contractName.toLowerCase())) {
                // 从 params 中提取 amount 和 merkleProof 参数
                BigInteger amount = null;
                List<byte[]> merkleProof = null;

                if (params != null && params.length >= 2) {
                    if (params[0] instanceof BigInteger) {
                        amount = (BigInteger) params[0];
                    }
                    if (params[1] instanceof List) {
                        merkleProof = (List<byte[]>) params[1];
                    }
                }

                return handleAirdropTransaction(methodName, params, amount, merkleProof);
            } else if ("dao".equals(contractName.toLowerCase())) {
                return handleDaoTransaction(methodName, params);
            } else if ("usdt".equals(contractName.toLowerCase())) {
                return handleUsdtTransaction(methodName, params);
            } else {
                throw new IllegalArgumentException("Unsupported contract: " + contractName);
            }
        } catch (Exception e) {
            log.error("Transaction failed for contract {} method {}", contractName, methodName, e);
            throw new RuntimeException("Transaction failed: " + e.getMessage(), e);
        }
    }


    /**
     * 获取交易详情
     * 数据流向：链上 → 链下读取
     */
    @Override
    public BlockchainTransactionDTO getTransaction(String txHash) {
        try {
            Transaction transaction = web3j.ethGetTransactionByHash(txHash)
                    .send()
                    .getTransaction()
                    .orElse(null);

            if (transaction == null) {
                log.warn("Transaction not found: {}", txHash);
                return null;
            }

            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash)
                    .send();

            BlockchainTransactionDTO dto = new BlockchainTransactionDTO();
            dto.setTxHash(txHash);
            dto.setFrom(transaction.getFrom());
            dto.setTo(transaction.getTo());
            dto.setValue(transaction.getValue());
            dto.setGasPrice(transaction.getGasPrice());

            if (receiptResponse.getTransactionReceipt().isPresent()) {
                TransactionReceipt receipt = receiptResponse.getTransactionReceipt().get();
                dto.setGasUsed(receipt.getGasUsed());
                dto.setBlockNumber(receipt.getBlockNumber().toString());
                dto.setStatus(receipt.isStatusOK() ? "success" : "failed");

                BigInteger blockNumber = receipt.getBlockNumber();
                long timestamp = web3j.ethGetBlockByNumber(
                                DefaultBlockParameterName.valueOf(blockNumber.toString()),
                                false
                        ).send()
                        .getBlock()
                        .getTimestamp()
                        .longValue();

                dto.setTimestamp(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamp),
                        ZoneId.systemDefault()
                ));
            } else {
                dto.setStatus("pending");
            }

            return dto;

        } catch (IOException e) {
            log.error("Get transaction failed for hash: {}", txHash, e);
            throw new RuntimeException("Get transaction failed: " + e.getMessage(), e);
        }
    }

    /**
     * 验证交易
     */
    @Override
    public boolean verifyTransaction(String txHash, String from, String to, BigDecimal amount) {
        try {
            // 去除空格并确保 txHash 有 0x 前缀
            if (txHash == null || txHash.trim().isEmpty()) {
                log.warn("Transaction hash is empty");
                return false;
            }

            txHash = txHash.trim();
            if (!txHash.startsWith("0x")) {
                txHash = "0x" + txHash;
            }

            log.info("Verifying transaction: {} from {} to {} amount {}",
                    txHash, from, to, amount);

            // 获取交易详情
            Transaction transaction = web3j.ethGetTransactionByHash(txHash)
                    .send()
                    .getTransaction()
                    .orElse(null);

            if (transaction == null) {
                log.warn("Transaction not found: {}", txHash);
                return false;
            }

            // 验证发送方
            if (!from.equalsIgnoreCase(transaction.getFrom())) {
                log.warn("Transaction sender mismatch. Expected: {}, Actual: {}",
                        from, transaction.getFrom());
                return false;
            }

            // 对于 ERC20 代币转账，不验证接收方地址
            // 因为 transaction.getTo() 返回的是代币合约地址，而不是实际接收方
            // 实际接收方编码在 transaction input data 中

            // 获取交易收据确认是否成功
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash)
                    .send();

            if (receiptResponse.getTransactionReceipt().isPresent()) {
                TransactionReceipt receipt = receiptResponse.getTransactionReceipt().get();

                if (!receipt.isStatusOK()) {
                    log.warn("Transaction execution failed: {}", txHash);
                    return false;
                }

                log.info("Transaction verified successfully: {}", txHash);
                return true;
            }

            // 如果没有收据，说明交易还在等待中
            log.warn("Transaction pending confirmation: {}", txHash);
            return false;

        } catch (IOException e) {
            log.error("Verify transaction failed for hash: {}, error: {}", txHash, e.getMessage());
            throw new RuntimeException("Verify transaction failed: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化事件订阅
     */
    @PostConstruct
    public void initEventSubscriptions() {
        log.info("Initializing event subscriptions...");
        try {
            // 等待合约加载完成
            Thread.sleep(2000);
            subscribeToEvents();
        } catch (Exception e) {
            log.error("Failed to initialize event subscriptions", e);
        }
    }

    // ============================================================
    // === Event Subscription（链上 → 链下）：监听事件同步
    // ============================================================

    /**
     * 订阅合约事件
     * 数据流向：链上事件 → 消息队列 → 链下监听器
     */
    @Override
    public void subscribeToEvents() {
        log.info("Subscribing to contract events...");

        try {
            Disposable transferSubscription = exthContract.transferEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleTransferEvent(event),
                            error -> log.error("Error in Transfer event subscription", error)
                    );
            disposables.add(transferSubscription);
            log.info("Subscribed to EXTH Transfer events");

            Disposable tradeMatchedSubscription = exchangeContract.tradeMatchedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleTradeMatchedEvent(event),
                            error -> log.error("Error in TradeMatched event subscription", error)
                    );
            disposables.add(tradeMatchedSubscription);
            log.info("Subscribed to Exchange TradeMatched events");

            Disposable tradeCompletedSubscription = exchangeContract.tradeCompletedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleTradeCompletedEvent(event),
                            error -> log.error("Error in TradeCompleted event subscription", error)
                    );
            disposables.add(tradeCompletedSubscription);
            log.info("Subscribed to Exchange TradeCompleted events");

            Disposable airdropClaimedSubscription = airdropContract.claimedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleAirdropClaimedEvent(event),
                            error -> log.error("Error in AirdropClaimed event subscription", error)
                    );
            disposables.add(airdropClaimedSubscription);
            log.info("Subscribed to Airdrop Claimed events");

            Disposable userBlacklistedSubscription = exchangeContract.userBlacklistedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleUserBlacklistedEvent(event),
                            error -> log.error("Error in UserBlacklisted event subscription", error)
                    );
            disposables.add(userBlacklistedSubscription);
            log.info("Subscribed to Exchange UserBlacklisted events");

            // ✅ 新增：订阅 DAO 的 ProposalExecuted 事件
            Disposable proposalExecutedSubscription = daoContract.proposalExecutedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleProposalExecutedEvent(event),
                            error -> log.error("Error in ProposalExecuted event subscription", error)
                    );
            disposables.add(proposalExecutedSubscription);
            log.info("Subscribed to DAO ProposalExecuted events");

            // 🔥 新增：订阅 DAO 的 VoteCast 事件
            Disposable voteCastSubscription = daoContract.voteCastEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleVoteCastEvent(event),
                            error -> log.error("Error in VoteCast event subscription", error)
                    );
            disposables.add(voteCastSubscription);
            log.info("Subscribed to DAO VoteCast events");

            // 🔥 新增：订阅 DAO 的 ProposalCreated 事件
            Disposable proposalCreatedSubscription = daoContract.proposalCreatedEventFlowable(
                            DefaultBlockParameterName.LATEST,
                            DefaultBlockParameterName.LATEST)
                    .subscribe(
                            event -> handleProposalCreatedEvent(event),
                            error -> log.error("Error in ProposalCreated event subscription", error)
                    );
            disposables.add(proposalCreatedSubscription);
            log.info("Subscribed to DAO ProposalCreated events");

            log.info("Event subscriptions initialized successfully. Total subscriptions: {}", disposables.size());

        } catch (Exception e) {
            log.error("Failed to subscribe to contract events", e);
            throw new RuntimeException("Event subscription failed: " + e.getMessage(), e);
        }
    }

    /**
     * 处理 Transfer 事件
     */
    private void handleTransferEvent(EXTH.TransferEventResponse event) {
        try {
            log.info("Processing Transfer event: from={} to={} value={}",
                    event.from, event.to, event.value);

            // 只发送消息到消息队列，由监听器处理业务逻辑
            sendTransferMessage(event);

        } catch (Exception e) {
            log.error("Error processing Transfer event", e);
        }
    }


    /**
     * 发送转账消息到消息队列
     */
    private void sendTransferMessage(EXTH.TransferEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "Transfer");
            message.put("from", event.from);
            message.put("to", event.to);
            message.put("value", event.value.toString());
            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("Transfer message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send transfer message: {}", e.getMessage());
        }
    }

    /**
     * 处理 TradeMatched 事件
     */
    private void handleTradeMatchedEvent(Exchange.TradeMatchedEventResponse event) {
        try {
            log.info("Processing TradeMatched event: tradeId={} partyA={} partyB={} amount={}",
                    event.tradeId, event.partyA, event.partyB, event.amount);

            // 保存链上索引 ID 到 Redis 的映射关系
            // 这样后续可以通过链上 ID 查询到数据库业务 ID
            String chainTradeKey = SystemConstants.RedisKey.TRADE_INFO + ":chain:" + event.tradeId;
            redisTemplate.opsForValue().set(chainTradeKey, event.tradeId.toString(), 24, TimeUnit.HOURS);

            // 只发送消息到消息队列，由监听器处理业务逻辑
            sendTradeMatchedMessage(event);

        } catch (Exception e) {
            log.error("Error processing TradeMatched event", e);
        }
    }

    /**
     * 发送交易匹配消息到消息队列
     */
    private void sendTradeMatchedMessage(Exchange.TradeMatchedEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "TradeMatched");
            message.put("tradeId", event.tradeId.toString());
            message.put("partyA", event.partyA);
            message.put("partyB", event.partyB);
            message.put("amount", event.amount.toString());
            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("Trade matched message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send trade matched message: {}", e.getMessage());
        }
    }

    /**
     * 处理 TradeCompleted 事件
     */
    private void handleTradeCompletedEvent(Exchange.TradeCompletedEventResponse event) {
        try {
            log.info("Processing TradeCompleted event: tradeId={}", event.tradeId);

            // 只发送消息到消息队列，由监听器处理业务逻辑
            sendTradeCompletedMessage(event);

        } catch (Exception e) {
            log.error("Error processing TradeCompleted event", e);
        }
    }

    /**
     * 发送交易完成消息到消息队列
     */
    private void sendTradeCompletedMessage(Exchange.TradeCompletedEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "TradeCompleted");
            message.put("tradeId", event.tradeId.toString());
            message.put("partyA", getPartyAFromTrade(event.tradeId));
            message.put("partyB", getPartyBFromTrade(event.tradeId));
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
     * 从缓存或数据库获取交易参与方 A
     */
    private String getPartyAFromTrade(BigInteger tradeId) {
        try {
            // 优先从 Redis 缓存获取
            String tradeKey = SystemConstants.RedisKey.TRADE_INFO + ":" + tradeId;
            String partyA = (String) redisTemplate.opsForHash().get(tradeKey, "partyA");

            if (partyA != null && !partyA.isEmpty()) {
                return partyA;
            }

            // 如果缓存没有，需要从数据库查询
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
            // 优先从 Redis 缓存获取
            String tradeKey = SystemConstants.RedisKey.TRADE_INFO + ":" + tradeId;
            String partyB = (String) redisTemplate.opsForHash().get(tradeKey, "partyB");

            if (partyB != null && !partyB.isEmpty()) {
                return partyB;
            }

            // 如果缓存没有，需要从数据库查询
            TradeDTO trade = tradeService.getTradeDetail(tradeId.toString());
            return trade != null ? trade.getPartyB() : null;

        } catch (Exception e) {
            log.warn("Failed to get partyB from trade: {}", e.getMessage());
            return null;
        }
    }


    /**
     * 处理 AirdropClaimed 事件
     */
    private void handleAirdropClaimedEvent(Airdrop.AirdropClaimedEventResponse event) {
        try {
            log.info("Processing AirdropClaimed event: recipient={} amount={}",
                    event.claimant, event.amount);

            // 只发送消息到消息队列，由监听器处理业务逻辑
            sendAirdropMessage(event);

        } catch (Exception e) {
            log.error("Error processing AirdropClaimed event", e);
        }
    }

    private void handleProposalExecutedEvent(Dao.ProposalExecutedEventResponse event) {
        try {
            log.info("Processing ProposalExecuted event: proposalId={}", event.proposalId);

            // 🔥 更新数据库中的提案状态和投票数据
            updateProposalRecordFromChain(event.proposalId, event.log.getBlockNumber().longValue());

            // 发送到消息队列，由监听器处理
            sendProposalExecutedMessage(event);

        } catch (Exception e) {
            log.error("Error processing ProposalExecuted event", e);
        }
    }

    // 🔥 新增：处理 VoteCast 事件
    private void handleVoteCastEvent(Dao.VoteCastEventResponse event) {
        try {
            log.info("Processing VoteCast event: proposalId={}, voter={}, weight={}",
                    event.proposalId, event.voter, event.weight);

            // 🔥 更新数据库中的投票数据
            updateProposalRecordFromChain(event.proposalId, event.log.getBlockNumber().longValue());

        } catch (Exception e) {
            log.error("Error processing VoteCast event", e);
        }
    }

    // 🔥 新增：处理 ProposalCreated 事件
    private void handleProposalCreatedEvent(Dao.ProposalCreatedEventResponse event) {
        try {
            log.info("Processing ProposalCreated event: proposalId={}, proposer={}",
                    event.proposalId, event.proposer);

            // 🔥 更新数据库中的提案信息
            updateProposalRecordFromChain(event.proposalId, event.log.getBlockNumber().longValue());

        } catch (Exception e) {
            log.error("Error processing ProposalCreated event", e);
        }
    }

    // 🔥 新增：通用方法 - 从链上同步提案数据到数据库
    private void updateProposalRecordFromChain(BigInteger proposalId, Long blockNumber) {
        CompletableFuture.runAsync(() -> {
            try {
                // 从链上获取最新的提案信息
                DaoWrapper.ProposalInfo proposalInfo = daoContract.getProposal(proposalId);

                // 查询数据库记录
                LambdaQueryWrapper<ProposalRecordEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(ProposalRecordEntity::getProposalId, proposalId.toString());
                ProposalRecordEntity dbRecord = proposalRecordMapper.selectOne(wrapper);

                if (dbRecord != null) {
                    // 🔥 同步所有链上数据
                    dbRecord.setBlockNumber(blockNumber);
                    dbRecord.setYesVotes(new BigDecimal(proposalInfo.yesVotes != null ? proposalInfo.yesVotes : BigInteger.ZERO));
                    dbRecord.setNoVotes(new BigDecimal(proposalInfo.noVotes != null ? proposalInfo.noVotes : BigInteger.ZERO));
                    dbRecord.setDeadline(proposalInfo.deadline != null ? proposalInfo.deadline.longValue() : null);
                    dbRecord.setSnapshotBlock(proposalInfo.snapshotBlock != null ? proposalInfo.snapshotBlock.longValue() : null);
                    dbRecord.setUpdatedAt(LocalDateTime.now());

                    proposalRecordMapper.updateById(dbRecord);
                    log.info("🔄 Synced proposal {} data from chain: blockNumber={}, deadline={}, yesVotes={}, noVotes={}",
                            proposalId, blockNumber, dbRecord.getDeadline(),
                            dbRecord.getYesVotes(), dbRecord.getNoVotes());
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to sync proposal data from chain: {}", e.getMessage());
            }
        });
    }

    /**
     * 发送提案执行消息到消息队列
     */
    private void sendProposalExecutedMessage(Dao.ProposalExecutedEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "ProposalExecuted");
            message.put("proposalId", event.proposalId.toString());
            /*message.put("targetContract", event.targetContract);*/
            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("Proposal executed message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send proposal executed message: {}", e.getMessage());
        }
    }

    /**
     * 处理 UserBlacklisted 事件
     */
    private void handleUserBlacklistedEvent(Exchange.UserBlacklistedEventResponse event) {
        try {
            log.info("Processing UserBlacklisted event: user={}", event.user);

            // ✅ MultiSigWallet 已经执行了 blacklistUser，链上状态已改变
            // ✅ 这里只需要发送消息到 MQ，让监听器更新数据库即可
            sendUserBlacklistMessage(event);

        } catch (Exception e) {
            log.error("Error processing UserBlacklisted event", e);
        }
    }


    /**
     * 发送用户拉黑消息到消息队列
     */
    private void sendUserBlacklistMessage(Exchange.UserBlacklistedEventResponse event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "UserBlacklisted");
            message.put("user", event.user);
            message.put("blockNumber", event.log.getBlockNumber());
            message.put("txHash", event.log.getTransactionHash());
            message.put("timestamp", System.currentTimeMillis());

            rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);

            log.debug("User blacklisted message sent to queue");
        } catch (Exception e) {
            log.warn("Failed to send user blacklist message: {}", e.getMessage());
        }
    }

    /**
     * 取消所有事件订阅（在应用关闭时调用）
     */
    public void unsubscribeFromEvents() {
        for (Disposable disposable : disposables) {
            try {
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            } catch (Exception e) {
                log.error("Error disposing subscription", e);
            }
        }
        disposables.clear();
        log.info("Unsubscribed from all contract events");
    }



    // ============================================================
    // === Read Operations（链上 → 链下）：查询链上数据
    // ============================================================

    /**
     * 获取指定代币余额
     * 数据流向：链上合约 → 链下读取
     */
    @Override
    public BigDecimal getBalance(String address, String tokenSymbol) {
        try {
            String symbol = tokenSymbol.toUpperCase();
            if ("EXTH".equals(symbol)) {
                BigInteger balance = exthContract.balanceOf(address);
                return Web3jUtil.fromChainUnit(balance);
            } else if ("USDT".equals(symbol)) {
                BigInteger balance = usdtContract.balanceOf(address);
                return Web3jUtil.fromChainUnit(balance);
            } else {
                throw new IllegalArgumentException("Unsupported token: " + tokenSymbol);
            }
        } catch (Exception e) {
            log.error("Get balance failed for {} token {}", address, tokenSymbol, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 在链上创建交易对
     * 数据流向：链下请求 → 链上合约
     */
    @Override
    public String createTradePairOnChain(String partyA, String partyB, BigDecimal amount) {
        try {
            log.info("Creating trade pair on chain: {} vs {}, amount {}",
                    partyA, partyB, amount);

            // 调用合约并获取链上索引 ID
            String chainTradeIdStr = exchangeContract.createTradePair(partyA, partyB, amount.toBigInteger());
            BigInteger chainTradeId = new BigInteger(chainTradeIdStr);

            log.info("Trade pair created on chain: chainTradeId={}", chainTradeId);

            return chainTradeIdStr;
        } catch (Exception e) {
            log.error("Create trade pair failed", e);
            throw new RuntimeException("Create trade pair failed: " + e.getMessage(), e);
        }
    }

    /**
     * 黑名单用户
     * 数据流向：链下请求 → 链上合约
     */
    @Override
    public String blacklistUserOnChain(String user) {
        try {
            log.info("Blacklisting user on chain: {}", user);
            return exchangeContract.blacklistUser(user);
        } catch (Exception e) {
            log.error("Blacklist user failed", e);
            throw new RuntimeException("Blacklist user failed: " + e.getMessage(), e);
        }
    }

    /**
     * 分发奖励
     * 数据流向：链下请求 → 链上合约
     */
    @Override
    public String distributeRewardOnChain(String user, BigInteger amount) {
        try {
            log.info("Distributing reward on chain: {} amount {}", user, amount);
            return exthContract.transfer(user, amount);
        } catch (Exception e) {
            log.error("Distribute reward failed", e);
            throw new RuntimeException("Distribute reward failed: " + e.getMessage(), e);
        }
    }


    /**
     * 处理 Exchange 合约交易
     */
    private String handleExchangeTransaction(String methodName, Object[] params) throws Exception {
        if ("createTradePair".equals(methodName)) {
            return exchangeContract.createTradePair(
                    (String) params[0], (String) params[1], (BigInteger) params[2]);
        } else if ("completeTrade".equals(methodName)) {
            return exchangeContract.completeTrade((BigInteger) params[0]);
        } else if ("disputeTrade".equals(methodName)) {
            return exchangeContract.disputeTrade(
                    (BigInteger) params[0], (String) params[1]);
        } else if ("blacklistUser".equals(methodName)) {
            return exchangeContract.blacklistUser((String) params[0]);
        } else if ("getUserInfo".equals(methodName)) {
            ExchangeWrapper.UserInfo info = exchangeContract.getUserInfo((String) params[0]);
            return info.toString();
        } else if ("getTradeInfo".equals(methodName)) {
            ExchangeWrapper.TradeInfo info = exchangeContract.getTradeInfo((BigInteger) params[0]);
            return info.toString();
        } else {
            throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }


    /**
     * 处理 EXTH 合约交易
     */
    private String handleExthTransaction(String methodName, Object[] params) throws Exception {
        if ("transfer".equals(methodName)) {
            return exthContract.transfer((String) params[0], (BigInteger) params[1]);
        } else if ("approve".equals(methodName)) {
            return exthContract.approve((String) params[0], (BigInteger) params[1]);
        } else if ("transferFrom".equals(methodName)) {
            return exthContract.transferFrom(
                    (String) params[0], (String) params[1], (BigInteger) params[2]);
        } else if ("balanceOf".equals(methodName)) {
            BigInteger balance = exthContract.balanceOf((String) params[0]);
            return balance.toString();
        } else if ("allowance".equals(methodName)) {
            BigInteger allowance = exthContract.allowance(
                    (String) params[0], (String) params[1]);
            return allowance.toString();
        } else if ("getVotes".equals(methodName)) {
            BigInteger votes = exthContract.getVotes((String) params[0]);
            return votes.toString();
        } else {
            throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }


    /**
     * 处理 Airdrop 合约交易
     */
    private String handleAirdropTransaction(String methodName, Object[] params,BigInteger amount, List<byte[]> merkleProof) throws Exception {
        if ("claimAirdrop".equals(methodName)) {
            return airdropContract.claimAirdrop(amount,merkleProof);
        } else if ("withdrawRemaining".equals(methodName)) {
            return airdropContract.withdrawRemaining();
        } else if ("isClaimed".equals(methodName)) {
            boolean claimed = airdropContract.isClaimed((String) params[0]);
            return String.valueOf(claimed);
        } else {
            throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    /**
     * 处理 DAO 合约交易
     */
    private String handleDaoTransaction(String methodName, Object[] params) throws Exception {
        if ("createProposal".equals(methodName)) {
            return daoContract.createProposal(
                    (String) params[1],
                    (BigInteger) params[2], (byte[]) params[3],(String) params[0]);
        } else if ("vote".equals(methodName)) {
            return daoContract.vote((BigInteger) params[0], (Boolean) params[1]);
        } else if ("queueProposal".equals(methodName)) {
            return daoContract.queueProposal((BigInteger) params[0]);
        } else if ("executeProposal".equals(methodName)) {
            return daoContract.executeProposal(
                    (BigInteger) params[0]);
        } else if ("cancelProposal".equals(methodName)) {
            return daoContract.cancelProposal((BigInteger) params[0]);
        } else if ("getProposalState".equals(methodName)) {
            BigInteger state = daoContract.getProposalState((BigInteger) params[0]);
            return state.toString();
        } else if ("getProposal".equals(methodName)) {
            DaoWrapper.ProposalInfo proposal = daoContract.getProposal((BigInteger) params[0]);
            return proposal.toString();
        } else {
            throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }

    /**
     * 处理 USDT 合约交易
     */
    private String handleUsdtTransaction(String methodName, Object[] params) throws Exception {
        if ("transfer".equals(methodName)) {
            return usdtContract.transfer((String) params[0], (BigInteger) params[1]);
        } else if ("approve".equals(methodName)) {
            return usdtContract.approve((String) params[0], (BigInteger) params[1]);
        } else if ("transferFrom".equals(methodName)) {
            return usdtContract.transferFrom(
                    (String) params[0], (String) params[1], (BigInteger) params[2]);
        } else if ("balanceOf".equals(methodName)) {
            BigInteger balance = usdtContract.balanceOf((String) params[0]);
            return balance.toString();
        } else if ("getName".equals(methodName)) {
            return usdtContract.getName();
        } else if ("getSymbol".equals(methodName)) {
            return usdtContract.getSymbol();
        } else if ("decimals".equals(methodName)) {
            return usdtContract.getDecimals().toString();
        } else {
            throw new IllegalArgumentException("Unknown method: " + methodName);
        }
    }
    /**
     * 从金库赔偿损失方（ETH）
     * 数据流向：链下请求 → 链上 Treasure 合约
     */
    @Override
    public String compensateFromTreasure(String victimAddress, BigInteger amount) {
        log.info("Compensating victim from Treasure: address={}, amount={}", victimAddress, amount);
        try {
            if (treasureContract == null || !treasureContract.isInitialized()) {
                log.warn("Treasure contract not initialized, skipping compensation");
                throw new IllegalStateException("Treasure contract not available");
            }
            String txHash = treasureContract.withdrawETHCompensation(victimAddress, amount);
            log.info("Compensation paid successfully from Treasure: txHash={}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to compensate from Treasure for {}: {}", victimAddress, e.getMessage());
            throw new RuntimeException("Failed to compensate from Treasure: " + e.getMessage(), e);
        }
    }
    /**
     * 从金库赔偿损失方（ERC20 代币）
     * 数据流向：链下请求 → 链上 Treasure 合约
     */
    @Override
    public String compensateERC20FromTreasure(String tokenAddress, String victimAddress, BigInteger amount) {
        log.info("Compensating victim from Treasure with ERC20: token={}, address={}, amount={}",
                tokenAddress, victimAddress, amount);
        try {
            if (treasureContract == null || !treasureContract.isInitialized()) {
                log.warn("Treasure contract not initialized, skipping compensation");
                throw new IllegalStateException("Treasure contract not available");
            }
            String txHash = treasureContract.withdrawERC20Compensation(tokenAddress, victimAddress, amount);
            log.info("ERC20 compensation paid successfully from Treasure: txHash={}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to compensate ERC20 from Treasure for {}: {}", victimAddress, e.getMessage());
            throw new RuntimeException("Failed to compensate ERC20 from Treasure: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 USDT 合约地址
     */
    @Override
    public String getUsdtContractAddress() {
        if (usdtContract != null) {
            return usdtContract.getContractAddress();
        }
        throw new IllegalStateException("USDT contract not initialized");
    }

    @Override
    public String getExthContractAddress() {
        if (exthContract != null) {
            return exthContract.getContractAddress();
        }
        throw new IllegalStateException("EXTH contract not initialized");
    }

    @Override
    public String getExchangeContractAddress() {
        if (exchangeContract != null) {
            return exchangeContract.getContractAddress();
        }
        throw new IllegalStateException("Exchange contract not initialized");
    }

    /**
     * 收取手续费
     * 数据流向：链下请求 → 链上合约
     */
    @Override
    public String collectFee(BigInteger tradeId, BigInteger feeAmount) {
        try {
            log.info("Collecting fee: tradeId={}, amount={}", tradeId, feeAmount);
            return exchangeContract.collectFee(tradeId, feeAmount);
        } catch (Exception e) {
            log.error("Collect fee failed", e);
            throw new RuntimeException("Collect fee failed: " + e.getMessage(), e);
        }
    }

    /**
     * 检查用户对 Exchange 合约的 EXTH 授权额度
     */
    @Override
    public BigInteger checkExthAllowance(String owner, String spender) throws Exception {
        if (exthContract == null) {
            throw new IllegalStateException("EXTH contract not initialized");
        }

        // EXTHWrapper.allowance() 已经调用了 send()，直接返回 BigInteger
        BigInteger allowance = exthContract.allowance(owner, spender);

        log.info("Check allowance: owner={}, spender={}, allowance={}", owner, spender, allowance);
        return allowance;
    }

    /**
     * 授权 Exchange 合约使用 EXTH 代币
     */
    @Override
    public String approveExth(String spender, BigInteger amount) throws Exception {
        if (exthContract == null) {
            throw new IllegalStateException("EXTH contract not initialized");
        }

        // EXTHWrapper.approve() 已经调用了 send() 并返回交易哈希
        String txHash = exthContract.approve(spender, amount);

        log.info("Approve EXTH: spender={}, amount={}, txHash={}", spender, amount, txHash);
        return txHash;
    }


    @Override
    public String getTreasureContractAddress() {
        // 从配置文件或环境变量中获取
        return System.getenv("TREASURE_CONTRACT_ADDRESS");
    }

    @Override
    public String getMultiSigWalletAddress() {
        // 从配置文件或环境变量中获取
        return System.getenv("MULTISIG_WALLET_ADDRESS");
    }
}



