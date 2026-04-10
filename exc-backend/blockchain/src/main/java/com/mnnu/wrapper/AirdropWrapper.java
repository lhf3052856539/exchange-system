package com.mnnu.wrapper;


import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.Airdrop;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Numeric;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class AirdropWrapper extends BaseWrapper{

    @Value("${contract.airdrop.address}")
    private String contractAddress;

    private Airdrop contract;

    private final EXTHWrapper exthWrapper;

    public AirdropWrapper(Web3j web3j, TransactionManager transactionManager,
                          ContractGasProvider gasProvider, EXTHWrapper exthWrapper) {
        super(web3j, transactionManager, gasProvider);
        this.exthWrapper = exthWrapper;
    }


    @PostConstruct
    public void init() {
        contract =Airdrop.load(contractAddress, web3j, transactionManager, gasProvider);
        log.info("Airdrop contract loaded: {}", contractAddress);
    }

    /**
     * 领取空投
     */
    public String claimAirdrop(BigInteger amount, List<byte[]> merkleProof) throws Exception {
        return contract.claim(amount, merkleProof)
                .send()
                .getTransactionHash();
    }

    /**
     * 查询 Airdrop 合约的 EXTH 代币余额
     */
    public BigInteger getAirdropBalance() throws Exception {
        try {
            // 获取 Airdrop 合约地址
            String airdropAddress = contract.getContractAddress();

            // 使用 EXTH Wrapper 查询余额
            return exthWrapper.balanceOf(airdropAddress);

        } catch (Exception e) {
            log.error("Failed to get airdrop balance", e);
            throw e;
        }
    }


    /**
     * 提取未空投完的代币（管理员）
     */
    public String withdrawRemaining() throws Exception {
        return contract.reclaimTokens()
                .send()
                .getTransactionHash();
    }

    /**
     * 查询指定地址是否已领取空投
     */
    public boolean isClaimed(String address) throws Exception {
        return contract.isClaimed(address).send();
    }



    /**
     * 获取合约地址
     */
    public String getContractAddress() {
        return contract.getContractAddress();
    }

    /**
     * 监听 Claimed 事件
     */
    public Flowable<Airdrop.AirdropClaimedEventResponse> claimedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.airdropClaimedEventFlowable(startBlock, endBlock);
    }

    /**
     * 获取交易收据中的 Claimed 事件
     */
    public List<Airdrop.AirdropClaimedEventResponse> getClaimedEvents(TransactionReceipt receipt) {
        return Airdrop.getAirdropClaimedEvents(receipt);
    }


}
