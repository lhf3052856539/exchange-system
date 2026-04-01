package com.mnnu.wrapper;


import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.Dao;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple11;
import org.web3j.tuples.generated.Tuple12;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class DaoWrapper extends BaseWrapper{

    @Value("${contract.dao.address}")
    private String contractAddress;

    private Dao contract;

    public DaoWrapper(Web3j web3j, TransactionManager transactionManager,
                      ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
    }

    @PostConstruct
    public void init() {
        contract =Dao.load(contractAddress, web3j, transactionManager, gasProvider);
        log.info("DAO contract loaded: {}", contractAddress);
    }

    /**
     * 创建提案
     */
    public String createProposal(String targetContract,
                                 BigInteger value, byte[] callData,String description) throws Exception {
        TransactionReceipt receipt = contract.propose(targetContract,
                        value, callData,description)
                .send();

        // 调试：打印交易哈希和日志数量
        log.info("CreateProposal Tx Hash: {}", receipt.getTransactionHash());
        log.info("Receipt Logs Count: {}", receipt.getLogs().size());

        return receipt.getTransactionHash();
    }

    /**
     * 获取提案创建事件
     */
    public List<Dao.ProposalCreatedEventResponse> getProposalCreatedEvents(TransactionReceipt receipt) {
        // 使用静态方法解析事件（正确方式）
        return Dao.getProposalCreatedEvents(receipt);
    }

    /**
     * 投票
     */
    public String vote(BigInteger proposalId, boolean support) throws Exception {
        return contract.vote(proposalId, support)
                .send()
                .getTransactionHash();
    }

    /**
     * 将提案加入公示期队列
     */
    public String queueProposal(BigInteger proposalId) throws Exception {
        return contract.queue(proposalId)
                .send()
                .getTransactionHash();
    }

    /**
     * 执行提案
     */
    public String executeProposal(BigInteger proposalId) throws Exception {
        // ✅ 先查询提案信息获取 eta
        ProposalInfo info = getProposal(proposalId);

        log.info("=== Execute Proposal {} ===", proposalId);
        log.info("eta: {}", info.eta);
        log.info("target: {}", info.targetContract);
        log.info("value: {}", info.value);
        log.info("callData length: {}", info.callData != null ? info.callData.length : 0);
        if (info.callData != null && info.callData.length > 0) {
            log.info("callData: 0x{}", bytesToHex(info.callData));
        }

        if (info.eta == null || info.eta.compareTo(BigInteger.ZERO) <= 0) {
            throw new Exception("Proposal eta not set");
        }

        // ✅ 传递 proposalId 和 wei（0，因为不需要发送以太币）
        try {
            return contract.execute(proposalId, BigInteger.ZERO)
                    .send()
                    .getTransactionHash();
        } catch (Exception e) {
            log.error("Execute proposal failed: {}", e.getMessage());
            log.error("Transaction details:");
            log.error("  - proposalId: {}", proposalId);
            log.error("  - target: {}", info.targetContract);
            log.error("  - value: {}", info.value);
            log.error("  - eta: {}", info.eta);
            throw e;
        }
    }

    // 添加一个辅助方法将字节数组转换为十六进制字符串
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 取消提案
     */
    public String cancelProposal(BigInteger proposalId) throws Exception {
        return contract.cancel(proposalId)
                .send()
                .getTransactionHash();
    }

    /**
     * 获取提案状态
     */
    public BigInteger getProposalState(BigInteger proposalId) throws Exception {
        return contract.state(proposalId).send();
    }

    /**
     * 获取提案详情
     */
    public ProposalInfo getProposal(BigInteger proposalId) throws Exception {
        Tuple12<String, BigInteger, BigInteger, BigInteger, Boolean, Boolean, String, BigInteger, byte[], byte[], BigInteger, BigInteger> result = contract.proposals(proposalId).send();

        return new ProposalInfo(
                result.component1(),   // description
                result.component2(),   // deadline
                result.component3(),   // yesVotes
                result.component4(),   // noVotes
                result.component5() != null && result.component5(),   // executed
                result.component6() != null && result.component6(),   // canceled
                result.component7(),   // targetContract
                result.component8(),   // value
                result.component9(),   // callData
                result.component10(),  // timelockId
                result.component12(),  // ✅ eta (component12)
                result.component11()   // ✅ snapshotBlock (component11)
        );
    }


    /**
     * 查询是否已投票
     */
    public boolean hasVoted(BigInteger proposalId, String voter) throws Exception {
        return contract.hasVoted(proposalId, voter).send();
    }

    /**
     * 获取提案总数
     */
    public BigInteger getProposalCount() throws Exception {
        return contract.proposalCount().send();
    }

    /**
     * 获取投票周期
     */
    public BigInteger getVotingPeriod() throws Exception {
        return contract.votingPeriod().send();
    }


    /**
     * 设置投票周期
     */
    public String setVotingPeriod(BigInteger newPeriod) throws Exception {
        return contract.setVotingPeriod(newPeriod)
                .send()
                .getTransactionHash();
    }

    /**
     * 监听 ProposalExecuted 事件
     */
    public Flowable<Dao.ProposalExecutedEventResponse> proposalExecutedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.proposalExecutedEventFlowable(startBlock, endBlock);
    }

    // 🔥 新增：监听 VoteCast 事件
    public Flowable<Dao.VoteCastEventResponse> voteCastEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.voteCastEventFlowable(startBlock, endBlock);
    }

    // 🔥 新增：监听 ProposalCreated 事件
    public Flowable<Dao.ProposalCreatedEventResponse> proposalCreatedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.proposalCreatedEventFlowable(startBlock, endBlock);
    }

    // 🔥 新增：获取交易收据中的 VoteCast 事件
    public List<Dao.VoteCastEventResponse> getVoteCastEvents(TransactionReceipt receipt) {
        return Dao.getVoteCastEvents(receipt);
    }

    /**
     * 获取交易收据中的 ProposalExecuted 事件
     */
    public List<Dao.ProposalExecutedEventResponse> getProposalExecutedEvents(TransactionReceipt receipt) {
        return Dao.getProposalExecutedEvents(receipt);
    }


    /**
     * 提案信息
     */
    public static class ProposalInfo {
        public String description;
        public BigInteger deadline;
        public BigInteger yesVotes;
        public BigInteger noVotes;
        public boolean executed;
        public boolean queued;
        public String targetContract;
        public BigInteger value;
        public byte[] callData;
        public byte[] additionalData;
        public BigInteger eta;
        public BigInteger snapshotBlock;
        public String proposer;
        public BigInteger startTime;

        public ProposalInfo(String description, BigInteger deadline, BigInteger yesVotes,
                            BigInteger noVotes, boolean executed, boolean queued,
                            String targetContract, BigInteger value, byte[] callData,
                            byte[] additionalData, BigInteger eta, BigInteger snapshotBlock) {
            this.description = description;
            this.deadline = deadline;
            this.yesVotes = yesVotes;
            this.noVotes = noVotes;
            this.executed = executed;
            this.queued = queued;
            this.targetContract = targetContract;
            this.value = value;
            this.callData = callData;
            this.additionalData = additionalData;
            this.eta = eta;
            this.snapshotBlock = snapshotBlock;
            // proposer 和 startTime 暂时为 null，会在 getProposal 方法中设置
        }
    }
}