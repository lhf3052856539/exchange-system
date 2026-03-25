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
import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class AirdropWrapper extends BaseWrapper{

    @Value("${contract.airdrop.address}")
    private String contractAddress;

    private Airdrop contract;

    public AirdropWrapper(Web3j web3j, TransactionManager transactionManager,
                          ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
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
