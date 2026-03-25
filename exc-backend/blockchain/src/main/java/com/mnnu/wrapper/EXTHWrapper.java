package com.mnnu.wrapper;
/**
 * EXTH代币合约包装类
 */

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.EXTH;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class EXTHWrapper extends BaseWrapper{

    @Value("${contract.exth.address}")
    private String contractAddress;


    private EXTH contract;

    public EXTHWrapper(Web3j web3j, TransactionManager transactionManager,
                       ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
    }

    @PostConstruct
    public void init() {
        contract =EXTH.load(contractAddress, web3j, transactionManager, gasProvider);
        log.info("EXTH contract loaded: {}", contractAddress);
    }

    /**
     * 转账
     */
    public String transfer(String to, BigInteger amount) throws Exception {
        return contract.transfer(to, amount)
                .send()
                .getTransactionHash();
    }

    /**
     * 获取余额
     */
    public BigInteger balanceOf(String address) throws Exception {
        return contract.balanceOf(address).send();
    }

    /**
     * 获取投票权
     */
    public BigInteger getVotes(String address) throws Exception {
        return contract.getVotes(address).send();
    }


    /**
     * 获取历史投票权
     */
    public BigInteger getPastVotes(String address, BigInteger blockNumber) throws Exception {
        return contract.getPastVotes(address, blockNumber).send();
    }

    /**
     * 授权
     */
    public String approve(String spender, BigInteger amount) throws Exception {
        return contract.approve(spender, amount)
                .send()
                .getTransactionHash();
    }

    /**
     * 从授权账户转账
     */
    public String transferFrom(String from, String to, BigInteger amount) throws Exception {
        return contract.transferFrom(from, to, amount)
                .send()
                .getTransactionHash();
    }

    /**
     * 查询授权额度
     */
    public BigInteger allowance(String owner, String spender) throws Exception {
        return contract.allowance(owner, spender).send();
    }

    /**
     * 获取代币名称
     */
    public String getName() throws Exception {
        return contract.name().send();
    }

    /**
     * 获取代币符号
     */
    public String getSymbol() throws Exception {
        return contract.symbol().send();
    }

    /**
     * 获取代币精度
     */
    public BigInteger getDecimals() throws Exception {
        return contract.decimals().send();
    }

    /**
     * 获取总供应量
     */
    public BigInteger getTotalSupply() throws Exception {
        return contract.totalSupply().send();
    }


    /**
     * 获取合约地址
     */
    public String getContractAddress() {
        return contract.getContractAddress();
    }

    /**
     * 监听 Transfer 事件
     */
    public Flowable<EXTH.TransferEventResponse> transferEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.transferEventFlowable(startBlock, endBlock);
    }

    /**
     * 获取交易收据中的 Transfer 事件
     */
    public List<EXTH.TransferEventResponse> getTransferEvents(TransactionReceipt receipt) {
        return EXTH.getTransferEvents(receipt);
    }
}