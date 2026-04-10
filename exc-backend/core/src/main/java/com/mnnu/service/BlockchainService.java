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
     * 检查用户在链上 Exchange 合约中是否已注册
     */
    boolean isUserRegisteredOnChain(String address);

    /**
     * 获取用户余额
     */
    BigDecimal getBalance(String address, String tokenSymbol);



    /**
     * 从链上同步用户 EXTH 余额到数据库
     */
    void updateExthBalanceOnChain(String address);

    /**
     * 链上链下数据对账（修复不一致状态）
     */
    void reconcilePendingTrades();


    /**
     * 获取 USDT 合约地址
     */
    String getUsdtContractAddress();

    /**
     * 获取 EXTH 合约地址
     */
    String getExthContractAddress();

    /**
     * 获取 Exchange 合约地址
     */
    String getExchangeContractAddress();

    /**
     * 检查用户对 Exchange 合约的 EXTH 授权额度
     */
    java.math.BigInteger checkExthAllowance(String owner, String spender) throws Exception;

    /**
     * 授权 Exchange 合约使用 EXTH 代币
     */
    String approveExth(String spender, BigInteger amount) throws Exception;

    /**
     * 获取 Treasure 金库合约地址
     */
    String getTreasureContractAddress();

    /**
     * 获取多签钱包合约地址
     */
    String getMultiSigWalletAddress();
}