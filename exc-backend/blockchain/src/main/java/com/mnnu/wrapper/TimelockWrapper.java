package com.mnnu.wrapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.Timelock;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tuples.generated.Tuple5;

import javax.annotation.PostConstruct;
import java.math.BigInteger;

@Slf4j
@Component
public class TimelockWrapper extends BaseWrapper{

    @Value("${contract.timelock.address}")
    private String contractAddress;


    private Timelock contract;

    public TimelockWrapper(Web3j web3j, TransactionManager transactionManager,
                           ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
    }

    @PostConstruct
    public void init() {
        contract =Timelock.load(contractAddress, web3j, transactionManager, gasProvider);
        log.info("Timelock contract loaded: {}", contractAddress);
    }

    /**
     * 将交易加入队列
     */
    public String queueTransaction(String target, BigInteger value,
                                   byte[] data, BigInteger timestamp) throws Exception {
        return contract.queueTransaction(target, value,
                        data)
                .send()
                .getTransactionHash();
    }

    /**
     * 执行交易
     */
    public String executeTransaction(String target, BigInteger value,
                                     byte[] data, BigInteger timestamp) throws Exception {
        return contract.executeTransaction(target, value,
                        data, timestamp, BigInteger.ZERO)
                .send()
                .getTransactionHash();
    }

    /**
     * 取消交易
     */
    public String cancelTransaction(String target, BigInteger value,
                                    byte[] data, BigInteger timestamp) throws Exception {
        return contract.cancelTransaction(target, value,
                        data, timestamp)
                .send()
                .getTransactionHash();
    }

    /**
     * 设置延迟时间（管理员）
     */
    public String setDelay(BigInteger newDelay) throws Exception {
        return contract.updateMinDelay(newDelay)
                .send()
                .getTransactionHash();
    }

    /**
     * 检查交易是否已排队
     */
    public boolean isQueued(byte[] txId) throws Exception {
        return contract.isQueued(txId).send();
    }


    /**
     * 获取延迟时间
     */
    public BigInteger getDelay() throws Exception {
        return contract.minDelay().send();
    }

    /**
     * 获取交易 ID
     */
    public byte[] getTransactionId(String target, BigInteger value,
                                   byte[] data, BigInteger timestamp) throws Exception {
        return contract.getTransactionId(target, value,
                data, timestamp).send();
    }

    /**
     * 交易信息
     */
    public static class TransactionInfo {
        public String target;
        public BigInteger value;
        public byte[] data;
        public BigInteger timestamp;
        public boolean queued;

        public TransactionInfo(String target, BigInteger value, byte[] data,
                               BigInteger timestamp, boolean queued) {
            this.target = target;
            this.value = value;
            this.data = data;
            this.timestamp = timestamp;
            this.queued = queued;
        }
    }
}

