package com.mnnu.wrapper;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.MultiSigWallet;
import org.web3j.model.Treasure;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import javax.annotation.PostConstruct;
import java.math.BigInteger;

@Slf4j
@Component
public class TreasureWrapper extends BaseWrapper{

    @Value("${treasure.contract-address:}")
    private String contractAddress;


    private Treasure contract;

    public TreasureWrapper(Web3j web3j, TransactionManager transactionManager,
                           ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
    }

    @PostConstruct
    public void init() {
        if (contractAddress != null && !contractAddress.isEmpty()) {
            this.contract =Treasure.load(contractAddress, web3j, transactionManager, gasProvider);
            log.info("Treasure contract loaded at: {}", contractAddress);
        } else {
            log.warn("TREASURE_CONTRACT_ADDRESS not set, Treasure contract not available");
        }
    }

    /**
     * 从金库提取 ETH 赔偿给用户
     * @param to 接收赔偿的地址
     * @param amount 赔偿金额（Wei 单位）
     * @return 交易哈希
     */
    public String withdrawETHCompensation(String to, BigInteger amount) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Treasure contract not initialized");
        }

        try {
            log.info("Withdrawing ETH compensation from Treasure: to={}, amount={}", to, amount);

            // 调用智能合约的 withdrawETH 方法
            TransactionReceipt receipt = contract.withdrawETH(to, amount).send();

            String txHash = receipt.getTransactionHash();
            log.info("ETH withdrawal completed, txHash: {}", txHash);

            return txHash;
        } catch (Exception e) {
            log.error("Failed to withdraw ETH compensation: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 从金库提取 ERC20 代币赔偿给用户
     * @param tokenAddress ERC20 代币合约地址
     * @param to 接收赔偿的地址
     * @param amount 赔偿金额
     * @return 交易哈希
     */
    public String withdrawERC20Compensation(String tokenAddress, String to, BigInteger amount) throws Exception {
        if (contract == null) {
            throw new IllegalStateException("Treasure contract not initialized");
        }

        try {
            log.info("Withdrawing ERC20 compensation from Treasure: token={}, to={}, amount={}",
                    tokenAddress, to, amount);

            // 调用智能合约的 withdrawERC20 方法
            TransactionReceipt receipt = contract.withdrawERC20(tokenAddress, to, amount).send();

            String txHash = receipt.getTransactionHash();
            log.info("ERC20 withdrawal completed, txHash: {}", txHash);

            return txHash;
        } catch (Exception e) {
            log.error("Failed to withdraw ERC20 compensation: {}", e.getMessage());
            throw e;
        }
    }

    // 赔偿支付事件订阅流
    public Flowable<Treasure.CompensationPaidEventResponse> compensationPaidEventFlowable(DefaultBlockParameterName startBlock, DefaultBlockParameterName endBlock) {
        return contract.compensationPaidEventFlowable(startBlock, endBlock);
    }

    /**
     * 获取金库合约地址
     */
    public String getContractAddress() {
        return contract != null ? contract.getContractAddress() : null;
    }

    /**
     * 检查合约是否已初始化
     */
    public boolean isInitialized() {
        return contract != null;
    }
}
