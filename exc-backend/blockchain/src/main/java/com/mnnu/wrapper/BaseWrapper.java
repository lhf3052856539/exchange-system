package com.mnnu.wrapper;

import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

@Slf4j
public abstract class BaseWrapper {

    protected final Web3j web3j;
    protected final TransactionManager transactionManager;
    protected final ContractGasProvider gasProvider;

    public BaseWrapper(Web3j web3j, TransactionManager transactionManager, ContractGasProvider gasProvider) {
        this.web3j = web3j;
        this.transactionManager = transactionManager;
        this.gasProvider = gasProvider;
    }

    /**
     * 等待节点同步完成
     */
    protected void waitForNodeSync() throws Exception {
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
                if (blockNumber.getBlockNumber().compareTo(java.math.BigInteger.ZERO) > 0) {
                    log.info("Node synced, latest block: {}", blockNumber.getBlockNumber());
                    return;
                }
            } catch (Exception e) {
                log.warn("Node not ready, retrying... ({}/{})", retryCount + 1, maxRetries);
            }

            retryCount++;
            Thread.sleep(1000);
        }

        throw new RuntimeException("Node failed to sync after " + maxRetries + " retries");
    }
}
