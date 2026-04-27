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
import org.web3j.tuples.generated.*;
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
     * 完成交易
     */
    public String completeTrade(BigInteger tradeId) throws Exception {
        return contract.completeTrade(tradeId)
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
        Tuple10<String, String, BigInteger, BigInteger,BigInteger,BigInteger,BigInteger, String,BigInteger,BigInteger> result = contract.getTradeInfo(tradeId).send();
        return new TradeInfo(result.component1(), result.component2(), result.component3(),
                result.component4(), result.component5(), result.component6(), result.component7(), result.component8(),result.component9(),result.component10());
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
        public BigInteger feeAmount;
        public BigInteger state;
        public BigInteger disputeStatus;
        public String disputedParty;
        public BigInteger completeTime;
        public BigInteger createTime;

        public TradeInfo(String partyA, String partyB, BigInteger amount,
                         BigInteger exthReward,BigInteger feeAmount,BigInteger state,BigInteger disputeStatus, String disputedParty,BigInteger completeTime,
                         BigInteger createTime) {
            this.partyA = partyA;
            this.partyB = partyB;
            this.amount = amount;
            this.exthReward = exthReward;
            this.feeAmount = feeAmount;
            this.state = state;
            this.disputeStatus = disputeStatus;
            this.disputedParty = disputedParty;
            this.completeTime = completeTime;
            this.createTime = createTime;
        }


        /**
         * 获取合约地址
         */
        public String getContractAddress() {
            return contract.getContractAddress();
        }

    }

    /**
     * 注册用户
     */
    public String registerUser() throws Exception {
        return contract.registerUser().send().getTransactionHash();
    }
    /**
     * 监听 TradeCreate 事件
     */
    public Flowable<Exchange.TradeCreateEventResponse> tradeCreateEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeCreateEventFlowable(startBlock, endBlock);
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

    /**
     * 监听 UserUpgraded 事件
     */
    public Flowable<Exchange.UserUpgradedEventResponse> userUpgradedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.userUpgradedEventFlowable(startBlock, endBlock);
    }

    /**
     * 标记交易为过期状态
     */
    public TransactionReceipt expireTrade(BigInteger tradeId) throws Exception {
        return contract.expireTrade(tradeId).send();
    }

    /**
     * 监听 TradeExpired 事件 (交易过期)
     */
    public Flowable<Exchange.TradeExpiredEventResponse> tradeExpiredEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeExpiredEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 PartyAConfirmed 事件
     */
    public Flowable<Exchange.PartyAConfirmedEventResponse> partyAConfirmedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.partyAConfirmedEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 PartyBConfirmed 事件
     */
    public Flowable<Exchange.PartyBConfirmedEventResponse> partyBConfirmedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.partyBConfirmedEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 TradeCancelled 事件
     */
    public Flowable<Exchange.TradeCancelledEventResponse> tradeCancelledEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeCancelledEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 TradeDisputed 事件
     */
    public Flowable<Exchange.TradeDisputedEventResponse> tradeDisputedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeDisputedEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 TradeResolved 事件
     */
    public Flowable<Exchange.TradeResolvedEventResponse> tradeResolvedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.tradeResolvedEventFlowable(startBlock, endBlock);
    }

    // 手续费收取事件订阅流
    public Flowable<Exchange.FeeCollectedEventResponse> feeCollectedEventFlowable(DefaultBlockParameterName startBlock, DefaultBlockParameterName endBlock) {
        return contract.feeCollectedEventFlowable(startBlock, endBlock);
    }


    /**
     * 获取合约地址
     */
    public String getContractAddress() {
        if (contract != null) {
            return contract.getContractAddress();
        }
        return contractAddress; // 返回配置的地址
    }


}

