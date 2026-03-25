package com.mnnu.wrapper;
 /**
 * 合约包装类
 */

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.web3j.model.Exchange;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple5;
import org.web3j.tuples.generated.Tuple7;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Numeric;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;


/**
 * Exchange合约包装类
 */
@Slf4j
@Component
public class ExchangeWrapper extends BaseWrapper{

    @Value("${contract.exchange.address}")
    private String contractAddress;


    private static Exchange contract;

    public ExchangeWrapper(Web3j web3j, TransactionManager transactionManager,
                           ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);

    }

    @PostConstruct
    public void init() {
        // --- 开始诊断性日志 ---
        log.info("==================== WEB3J DIAGNOSTIC START ====================");
        log.info("Attempting to load Exchange contract...");

        if (contractAddress == null) {
            log.error("CRITICAL: contractAddress is NULL. @Value injection failed. Check your application.yml path.");
            throw new IllegalArgumentException("Contract address is null.");
        }

        // 使用 [] 括起来，可以暴露前后可能存在的空格
        log.info("Raw contract address from @Value: '[{}]'", contractAddress);
        log.info("Address length: {}", contractAddress.length());

        // --- 手动进行地址格式校验，不依赖任何 web3j 的方法 ---
        // 一个合法的以太坊地址必须是：
        // 1. 以 "0x" 开头
        // 2. 总长度为 42 个字符
        // 3. "0x" 后面是 40 个十六进制字符 (0-9, a-f, A-F)
        boolean isValid = contractAddress != null && contractAddress.matches("^0x[0-9a-fA-F]{40}$");

        log.info("Is valid hex address format (manual check)? --> {}", isValid);

        if (!isValid) {
            log.error("CRITICAL: The contract address string IS NOT a valid hex address format!");
            log.error("This is the likely cause of the EnsResolutionException.");
            log.error("Please check your application.yml for hidden spaces, newlines, or other characters.");
            log.error("A valid address MUST start with '0x', be exactly 42 characters long, and contain only hex characters (0-9, a-f).");
            // 为了防止后续崩溃，这里可以直接抛出更明确的异常
            throw new IllegalArgumentException("Invalid contract address format: '" + contractAddress + "'");
        }
        log.info("==================== WEB3J DIAGNOSTIC END ======================");


        // 原始的、会失败的代码行
        contract = Exchange.load(contractAddress, web3j, transactionManager, gasProvider);

        log.info("Exchange contract loaded successfully: {}", contract.getContractAddress());
    }
    /**
     * 创建交易对
     */
    public String createTradePair(String partyA, String partyB, BigInteger amount) throws Exception {
        try {
            // 发送交易并获取回执
            TransactionReceipt receipt = contract.createTradePair(partyA, partyB, amount).send();

            log.info("Transaction sent: txHash={}", receipt.getTransactionHash());
            log.info("Transaction status: {}", receipt.getStatus());
            log.info("Gas used: {}", receipt.getGasUsed());

            // 打印所有日志
            if (receipt.getLogs() != null && !receipt.getLogs().isEmpty()) {
                log.info("Found {} logs in receipt", receipt.getLogs().size());
                for (int i = 0; i < receipt.getLogs().size(); i++) {
                    log.info("Log[{}]: topics={}, data={}",
                            i,
                            receipt.getLogs().get(i).getTopics(),
                            receipt.getLogs().get(i).getData());
                }
            } else {
                log.warn("No logs found in receipt!");
            }

            // 从事件中提取 tradeId
            List<Exchange.TradeMatchedEventResponse> events = getTradeMatchedEvents(receipt);
            log.info("Parsed {} TradeMatched events using web3j getter", events != null ? events.size() : 0);

            if (events != null && !events.isEmpty()) {
                BigInteger tradeId = events.get(0).tradeId;
                log.info("✅ Trade pair created on chain: tradeId={}, txHash={}", tradeId, receipt.getTransactionHash());
                return tradeId.toString();
            }

            // 如果 web3j 的事件解析失败，尝试手动解析
            log.warn("⚠️ Web3j event parsing failed, trying manual extraction...");

            // 手动解析第一个 topic 作为 tradeId（这是最后的手段）
            if (receipt.getLogs() != null && !receipt.getLogs().isEmpty()) {
                String firstTopic = receipt.getLogs().get(0).getTopics().get(1); // tradeId 通常是第二个 topic
                if (firstTopic != null) {
                    BigInteger manualTradeId = Numeric.decodeQuantity(firstTopic);
                    log.info("✅ Manually extracted tradeId: {}", manualTradeId);
                    return manualTradeId.toString();
                }
            }

            // 如果还是没有，返回交易哈希作为后备
            log.error("❌ All methods failed, returning txHash: {}", receipt.getTransactionHash());
            return receipt.getTransactionHash();

        } catch (Exception e) {
            log.error("❌ createTradePair failed: {}", e.getMessage(), e);
            throw e;
        }
    }


    /**
     * 完成交易
     */
    public String completeTrade(BigInteger tradeId) throws Exception {
        return contract.completeTrade(tradeId)
                .send()
                .getTransactionHash();
    }

    /**
     * 收取手续费
     */
    public String collectFee(BigInteger tradeId, BigInteger feeAmount) throws Exception {
        return contract.collectFee(tradeId, feeAmount)
                .send()
                .getTransactionHash();
    }

    /**
     * 发起争议
     */
    public String disputeTrade(BigInteger tradeId, String disputedParty) throws Exception {
        return contract.disputeTrade(tradeId, disputedParty)
                .send()
                .getTransactionHash();
    }

    /**
     * 拉黑用户
     */
    public String blacklistUser(String user) throws Exception {
        return contract.blacklistUser(user)
                .send()
                .getTransactionHash();
    }

    /**
     * 获取用户信息
     */
    public UserInfo getUserInfo(String user) throws Exception {
        Tuple5<BigInteger, BigInteger, BigInteger, BigInteger, Boolean> result = contract.getUserInfo(user).send();
        return new UserInfo(result.component1().intValue(), result.component2(), result.component5());
    }

    /**
     * 获取交易信息
     */
    public TradeInfo getTradeInfo(BigInteger tradeId) throws Exception {
        Tuple7<String, String, BigInteger, BigInteger, Boolean, Boolean, String> result = contract.getTradeInfo(tradeId).send();
        return new TradeInfo(result.component1(), result.component2(), result.component3(),
                result.component4(), result.component5(), result.component6(), result.component7());
    }


    /**
     * 用户信息
     */
    public static class UserInfo {
        public int userType;
        public BigInteger newUserTradeCount;
        public boolean isBlacklisted;

        public UserInfo(int userType, BigInteger newUserTradeCount, boolean isBlacklisted) {
            this.userType = userType;
            this.newUserTradeCount = newUserTradeCount;
            this.isBlacklisted = isBlacklisted;
        }
    }

    /**
     * 交易信息
     */
    public static class TradeInfo {
        public String partyA;
        public String partyB;
        public BigInteger amount;
        public BigInteger exthReward;
        public boolean isCompleted;
        public boolean isDisputed;
        public String disputedParty;

        public TradeInfo(String partyA, String partyB, BigInteger amount,
                         BigInteger exthReward, boolean isCompleted,
                         boolean isDisputed, String disputedParty) {
            this.partyA = partyA;
            this.partyB = partyB;
            this.amount = amount;
            this.exthReward = exthReward;
            this.isCompleted = isCompleted;
            this.isDisputed = isDisputed;
            this.disputedParty = disputedParty;
        }


        /**
         * 获取合约地址
         */
        public String getContractAddress() {
            return contract.getContractAddress();
        }

    }
    /**
     * 监听 TradeMatched 事件
     */
    public Flowable<Exchange.TradeMatchedEventResponse> tradeMatchedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeMatchedEventFlowable(startBlock, endBlock);
    }

    /**
     * 获取交易收据中的 TradeMatched 事件
     */
    public List<Exchange.TradeMatchedEventResponse> getTradeMatchedEvents(TransactionReceipt receipt) {
        return Exchange.getTradeMatchedEvents(receipt);
    }

    /**
     * 监听 TradeCompleted 事件
     */
    public Flowable<Exchange.TradeCompletedEventResponse> tradeCompletedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeCompletedEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 UserBlacklisted 事件
     */
    public Flowable<Exchange.UserBlacklistedEventResponse> userBlacklistedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.userBlacklistedEventFlowable(startBlock, endBlock);
    }
}

