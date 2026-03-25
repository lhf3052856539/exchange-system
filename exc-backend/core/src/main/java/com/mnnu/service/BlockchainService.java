package com.mnnu.service;
/**
 * 区块链交互服务
 */

import com.mnnu.dto.BlockchainTransactionDTO;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public interface BlockchainService {

    /**
     * 获取合约实例
     */
    Object getContract(String contractName);

    /**
     * 发送交易
     */
    String sendTransaction(String contractName, String methodName, Object... params);

    /**
     * 查询交易
     */
    BlockchainTransactionDTO getTransaction(String txHash);

    /**
     * 验证交易
     */
    boolean verifyTransaction(String txHash, String from, String to, BigDecimal amount);

    /**
     * 监听合约事件
     */
    void subscribeToEvents();

    /**
     * 获取用户余额
     */
    BigDecimal getBalance(String address, String tokenSymbol);

    /**
     * 创建交易对（链上）
     */
    String createTradePairOnChain(String partyA, String partyB, BigDecimal amount);

    /**
     * 拉黑用户（链上）
     */
    String blacklistUserOnChain(String user);

    /**
     * 发放奖励
     */
    String distributeRewardOnChain(String user, BigInteger amount);

    /**
     * 从链上同步用户 EXTH 余额到数据库
     */
    void updateExthBalanceOnChain(String address);

    /**
     * 从金库赔偿损失方（ETH）
     */
    String compensateFromTreasure(String victimAddress, BigInteger amount);

    /**
     * 从金库赔偿损失方（ERC20 代币）
     */
    String compensateERC20FromTreasure(String tokenAddress, String victimAddress, BigInteger amount);

    /**
     * 获取 USDT 合约地址
     */
    String getUsdtContractAddress();

    /**
     * 获取 EXTH 合约地址
     */
    String getExthContractAddress();

    /**
     * 收取手续费
     */
    String collectFee(BigInteger tradeId, BigInteger feeAmount);

    String claimAirdropOnChain(String address, BigInteger amount, List<byte[]> merkleProof);

    /**
     * 完成交易（调用 Exchange 合约）
     */
    String completeTrade(BigInteger tradeId);
}