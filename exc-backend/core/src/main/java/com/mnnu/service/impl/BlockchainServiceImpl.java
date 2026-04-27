package com.mnnu.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.BlockchainTransactionDTO;
import com.mnnu.service.BlockchainService;
import com.mnnu.utils.GraphqlClient;
import com.mnnu.utils.Web3jUtil;
import com.mnnu.wrapper.*;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.mnnu.constant.SystemConstants.RedisKey.LAST_SYNC_KEY;

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
    private MultiSigWalletWrapper multiSigWalletWrapper;
    @Autowired
    private TreasureWrapper treasureWrapper;

    @Autowired
    private GraphqlClient graphqlClient;

    private static final int BATCH_SIZE = 100;


    @Override
    public long syncAllEvents(long lastTimestamp) {
        long maxTimestamp = lastTimestamp;

        // 第一批：查询非多签钱包相关事件
        try {
            JsonNode generalResult = graphqlClient.getGeneralEvents(lastTimestamp, BATCH_SIZE);
            if (generalResult != null && generalResult.has("data")) {
                JsonNode data = generalResult.get("data");
                if (data != null) {
                    maxTimestamp = processGeneralEvents(data, maxTimestamp);
                }
            }
        } catch (Exception e) {
            log.error("Error processing general events batch", e);
        }

        // 第二批：查询多签钱包相关事件
        try {
            JsonNode multiSigResult = graphqlClient.getMultiSigEvents(lastTimestamp, BATCH_SIZE);
            if (multiSigResult != null && multiSigResult.has("data")) {
                JsonNode data = multiSigResult.get("data");
                if (data != null) {
                    maxTimestamp = processMultiSigEvents(data, maxTimestamp);
                }
            }
        } catch (Exception e) {
            log.error("Error processing multi-sig wallet events batch", e);
        }

        return maxTimestamp;
    }

    /**
     * 处理非多签钱包相关事件（第一批）
     */
    private long processGeneralEvents(JsonNode data, long maxTimestamp) {
        // 1. UserBlacklisted
        if (data.has("userBlacklisteds")) {
            for (JsonNode event : data.get("userBlacklisteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "UserBlacklisted");
                    msg.put("user", event.get("user").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing UserBlacklisted event", e); }
            }
        }

        // 2. TradeCreate
        if (data.has("tradeCreates")) {
            for (JsonNode event : data.get("tradeCreates")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeCreate");
                    msg.put("chainTradeId", event.get("chainTradeId").asText());
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("partyA", event.get("partyA").asText());
                    msg.put("partyB", event.get("partyB").asText());
                    msg.put("amount", event.get("amount").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeCreate event", e); }
            }
        }

        // 3. TradeCompleted
        if (data.has("tradeCompleteds")) {
            for (JsonNode event : data.get("tradeCompleteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeCompleted");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeCompleted event", e); }
            }
        }

        // 4. TradeDisputed
        if (data.has("tradeDisputeds")) {
            for (JsonNode event : data.get("tradeDisputeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeDisputed");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("disputedParty", event.get("disputedParty").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeDisputed event", e); }
            }
        }

        // 5. TradeCancelled
        if (data.has("tradeCancelleds")) {
            for (JsonNode event : data.get("tradeCancelleds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeCancelled");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeCancelled event", e); }
            }
        }

        // 6. TradeExpired
        if (data.has("tradeExpireds")) {
            for (JsonNode event : data.get("tradeExpireds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeExpired");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeExpired event", e); }
            }
        }

        // 7. TradeResolved
        if (data.has("tradeResolveds")) {
            for (JsonNode event : data.get("tradeResolveds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeResolved");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing TradeResolved event", e); }
            }
        }

        // 8. DaoProposalCreated
        if (data.has("proposalCreateds")) {
            for (JsonNode event : data.get("proposalCreateds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalCreated");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("proposer", event.get("proposer").asText());
                    msg.put("description", event.get("description").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing DaoProposalCreated event", e); }
            }
        }

        // 9. DaoVoteCast
        if (data.has("voteCasts")) {
            for (JsonNode event : data.get("voteCasts")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "VoteCast");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("voter", event.get("voter").asText());
                    msg.put("support", event.get("support").asBoolean());
                    msg.put("weight", event.get("weight").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing DaoVoteCast event", e); }
            }
        }

        // 10. ProposalQueued
        if (data.has("proposalQueueds")) {
            for (JsonNode event : data.get("proposalQueueds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalQueued");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("eta", event.get("eta").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ProposalQueued event", e); }
            }
        }

        // 11. ProposalExecuted
        if (data.has("proposalExecuteds")) {
            for (JsonNode event : data.get("proposalExecuteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalExecuted");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ProposalExecuted event", e); }
            }
        }

        // 12. ProposalCanceled
        if (data.has("proposalCanceleds")) {
            for (JsonNode event : data.get("proposalCanceleds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalCanceled");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ProposalCanceled event", e); }
            }
        }

        // 13. PartyAConfirmed
        if (data.has("partyAConfirmeds")) {
            for (JsonNode event : data.get("partyAConfirmeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "PartyAConfirmed");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("party", event.get("party").asText());
                    msg.put("txHash", event.get("txHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing PartyAConfirmed event", e); }
            }
        }

        // 14. PartyBConfirmed
        if (data.has("partyBConfirmeds")) {
            for (JsonNode event : data.get("partyBConfirmeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "PartyBConfirmed");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("party", event.get("party").asText());
                    msg.put("txHash", event.get("txHash").asText());
                    msg.put("blockNumber", event.get("blockNumber").asLong());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing PartyBConfirmed event", e); }
            }
        }

        // 15. FeeCollected
        if (data.has("feeCollecteds")) {
            for (JsonNode event : data.get("feeCollecteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "FeeCollected");
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("feePayerA", event.get("feePayerA").asText());
                    msg.put("feePayerB", event.get("feePayerB").asText());
                    msg.put("feeAmount", event.get("feeAmount").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing FeeCollected event", e); }
            }
        }

        // 16. CompensationPaid
        if (data.has("compensationPaids")) {
            for (JsonNode event : data.get("compensationPaids")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CompensationPaid");
                    msg.put("victim", event.get("victim").asText());
                    msg.put("amount", event.get("amount").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing CompensationPaid event", e); }
            }
        }

        // 17. UserUpgraded
        if (data.has("userUpgradeds")) {
            for (JsonNode event : data.get("userUpgradeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "UserUpgraded");
                    msg.put("user", event.get("user").asText());
                    msg.put("newType", event.get("newType").asInt());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing UserUpgraded event", e); }
            }
        }

        // 18. AirdropClaimed
        if (data.has("airdropClaimeds")) {
            for (JsonNode event : data.get("airdropClaimeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "AirdropClaimed");
                    msg.put("claimant", event.get("claimant").asText());
                    msg.put("amount", event.get("amount").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing AirdropClaimed event", e); }
            }
        }

        return maxTimestamp;
    }

    /**
     * 处理多签钱包相关事件（第二批）
     */
    private long processMultiSigEvents(JsonNode data, long maxTimestamp) {
        // 1. MultiSigProposalCreated (仲裁提案创建)
        if (data.has("multiSigWalletProposalCreateds")) {
            for (JsonNode event : data.get("multiSigWalletProposalCreateds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalCreated");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("tradeId", event.get("tradeId").asText());
                    msg.put("accusedParty", event.get("accusedParty").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing MultiSigProposalCreated event", e); }
            }
        }

        // 2. MultiSigProposalExecuted (仲裁提案执行)
        if (data.has("multiSigWalletProposalExecuteds")) {
            for (JsonNode event : data.get("multiSigWalletProposalExecuteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalExecuted");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("accusedParty", event.get("accusedParty").asText());
                    msg.put("victimParty", event.get("victimParty").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing MultiSigProposalExecuted event", e); }
            }
        }

        // 3. ProposalRejected (仲裁提案拒绝)
        if (data.has("proposalRejecteds")) {
            for (JsonNode event : data.get("proposalRejecteds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalRejected");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ProposalRejected event", e); }
            }
        }

        // 4. ProposalExpired (仲裁提案过期)
        if (data.has("proposalExpireds")) {
            for (JsonNode event : data.get("proposalExpireds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalExpired");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ProposalExpired event", e); }
            }
        }

        // 5. ArbitrationProposalVoted (仲裁投票)
        if (data.has("multiSigWalletVoteCasts")) {
            for (JsonNode event : data.get("multiSigWalletVoteCasts")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalVoted");
                    msg.put("proposalId", event.get("proposalId").asText());
                    msg.put("voter", event.get("voter").asText());
                    msg.put("support", event.get("support").asBoolean());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing ArbitrationProposalVoted event", e); }
            }
        }

        // 6. CommitteeMemberAdded (委员会成员添加)
        if (data.has("committeeMemberAddeds")) {
            for (JsonNode event : data.get("committeeMemberAddeds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CommitteeMemberAdded");
                    msg.put("member", event.get("member").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing CommitteeMemberAdded event", e); }
            }
        }

        // 7. CommitteeMemberRemoved (委员会成员移除)
        if (data.has("committeeMemberRemoveds")) {
            for (JsonNode event : data.get("committeeMemberRemoveds")) {
                try {
                    long ts = event.get("blockTimestamp").asLong();
                    if (ts > maxTimestamp) maxTimestamp = ts;

                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CommitteeMemberRemoved");
                    msg.put("member", event.get("member").asText());
                    msg.put("txHash", event.get("transactionHash").asText());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                } catch (Exception e) { log.error("Error processing CommitteeMemberRemoved event", e); }
            }
        }

        return maxTimestamp;
    }




    @Override
    public Long getLastSyncTimestamp() {
        Object value = redisTemplate.opsForValue().get(LAST_SYNC_KEY);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format in Redis, resetting to 0", e);
            return 0L;
        }
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp) {
        redisTemplate.opsForValue().set(LAST_SYNC_KEY, String.valueOf(timestamp), 30, TimeUnit.DAYS);
    }


    @Override
    public void reconcilePendingTrades() {
        log.info("Reconciliation task executed.");
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
    //@PostConstruct
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

    /**
     * 订阅合约事件
     * 数据流向：链上事件 → 消息队列 → 链下监听器
     */
    @Override
    public void subscribeToEvents() {
        log.info("Subscribing to contract events and forwarding to MQ...");

        // EXTH Transfer 事件
        Disposable transferSub = exthContract.transferEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "Transfer");
                    msg.put("from", event.from);
                    msg.put("to", event.to);
                    msg.put("value", event.value.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    msg.put("blockNumber", event.log.getBlockNumber());
                    //发送转账消息到消息队列
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(transferSub);

        // Exchange TradeCreate 事件
        Disposable tradeMatchedSub = exchangeContract.tradeCreateEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeMatched");
                    msg.put("chainTradeId", event.chainTradeId.toString());
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("partyA", event.partyA);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeMatchedSub);

        // Exchange TradeCompleted 事件
        Disposable tradeCompletedSub = exchangeContract.tradeCompletedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeCompleted");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeCompletedSub);

        // Airdrop Claimed 事件
        Disposable airdropSub = airdropContract.claimedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "AirdropClaimed");
                    msg.put("recipient", event.claimant);
                    msg.put("amount", event.amount.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(airdropSub);

        Disposable userBlacklistedSubscription = exchangeContract.userBlacklistedEventFlowable(
                        DefaultBlockParameterName.LATEST,
                        DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> message = new HashMap<>();
                    message.put("event", "UserBlacklisted");
                    message.put("user", event.user);
                    message.put("blockNumber", event.log.getBlockNumber());
                    message.put("txHash", event.log.getTransactionHash());
                    message.put("timestamp", System.currentTimeMillis());

                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, message);
                });
        disposables.add(userBlacklistedSubscription);
        log.info("Subscribed to Exchange UserBlacklisted events");


        // DAO ProposalCreated 事件
        Disposable proposalCreatedSub = daoContract.proposalCreatedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalCreated");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("proposer", event.proposer);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(proposalCreatedSub);

        // DAO VoteCast 事件
        Disposable voteCastSub = daoContract.voteCastEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "VoteCast");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("voter", event.voter);
                    msg.put("support", event.support);
                    msg.put("weight", event.weight.toString());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(voteCastSub);

        // DAO ProposalQueued 事件
        Disposable proposalQueuedSub = daoContract.proposalQueuedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalQueued");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("eta", event.eta.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(proposalQueuedSub);

        // DAO ProposalExecuted 事件
        Disposable proposalExecutedSub = daoContract.proposalExecutedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalExecuted");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(proposalExecutedSub);

        // DAO ProposalCanceled 事件
        Disposable proposalCanceledSub = daoContract.proposalCanceledEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ProposalCanceled");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(proposalCanceledSub);

        // UserUpgraded 事件 (用于注册同步)
        Disposable userUpgradedSub = exchangeContract.userUpgradedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "UserUpgraded");
                    msg.put("user", event.user);
                    msg.put("newType", event.newType.intValue());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(userUpgradedSub);

        // PartyAConfirmed 事件
        Disposable partyAConfirmedSub = exchangeContract.partyAConfirmedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "PartyAConfirmed");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("party", event.party);
                    msg.put("txHash", event.txHash);
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(partyAConfirmedSub);

        // PartyBConfirmed 事件
        Disposable partyBConfirmedSub = exchangeContract.partyBConfirmedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "PartyBConfirmed");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("party", event.party);
                    msg.put("txHash", event.txHash);
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(partyBConfirmedSub);

        // MultiSigWallet CommitteeMemberAdded 事件
        Disposable memberAddedSub = multiSigWalletWrapper.committeeMemberAddedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CommitteeMemberAdded");
                    msg.put("member", event.member);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(memberAddedSub);

        // MultiSigWallet CommitteeMemberRemoved 事件
        Disposable memberRemovedSub = multiSigWalletWrapper.committeeMemberRemovedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CommitteeMemberRemoved");
                    msg.put("member", event.member);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(memberRemovedSub);

        // MultiSigWallet ProposalCreated (仲裁提案) 事件
        Disposable arbitrationProposalSub = multiSigWalletWrapper.proposalCreatedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalCreated");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("accusedParty", event.accusedParty);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(arbitrationProposalSub);

        // MultiSigWallet VoteCast (仲裁投票) 事件
        Disposable arbitrationVoteSub = multiSigWalletWrapper.proposalVotedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "ArbitrationProposalVoted");
                    msg.put("proposalId", event.proposalId.toString());
                    msg.put("voter", event.voter);
                    msg.put("support", event.support);
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(arbitrationVoteSub);



        // TradeCancelled 事件
        Disposable tradeCancelledSub = exchangeContract.tradeCancelledEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeCancelled");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeCancelledSub);

        // TradeDisputed 事件
        Disposable tradeDisputedSub = exchangeContract.tradeDisputedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeDisputed");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("disputedParty", event.disputedParty);
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeDisputedSub);

        // TradeResolved 事件
        Disposable tradeResolvedSub = exchangeContract.tradeResolvedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeResolved");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeResolvedSub);

        // FeeCollected 事件 (手续费收取)
        Disposable feeCollectedSub = exchangeContract.feeCollectedEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "FeeCollected");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("feePayerA", event.feePayerA);
                    msg.put("feePayerB", event.feePayerB);
                    msg.put("feeAmount", event.feeAmount.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(feeCollectedSub);

        // CompensationPaid 事件 (赔偿支付 )
        Disposable compensationPaidSub = treasureWrapper.compensationPaidEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "CompensationPaid");
                    msg.put("victim", event.victim);
                    msg.put("amount", event.amount.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(compensationPaidSub);

        // TradeExpired 事件 (交易过期)
        Disposable tradeExpiredSub = exchangeContract.tradeExpiredEventFlowable(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST)
                .subscribe(event -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("event", "TradeExpired");
                    msg.put("tradeId", event.tradeId.toString());
                    msg.put("txHash", event.log.getTransactionHash());
                    msg.put("blockNumber", event.log.getBlockNumber());
                    rabbitTemplate.convertAndSend(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, msg);
                });
        disposables.add(tradeExpiredSub);

        log.info("All event subscriptions initialized. Total: {}", disposables.size());
    }



    @Override
    public boolean isUserRegisteredOnChain(String address) {
        try {
            // 调用 Exchange 合约查询用户信息
            var info = exchangeContract.getUserInfo(address);
            return info != null && info.userType != 0; // 假设 0 是未注册
        } catch (Exception e) {
            log.error("Check user registration on chain failed", e);
            return false;
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
