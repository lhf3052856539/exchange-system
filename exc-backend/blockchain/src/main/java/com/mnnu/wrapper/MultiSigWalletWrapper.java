package com.mnnu.wrapper;

import io.reactivex.Flowable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.model.MultiSigWallet;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple10;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class MultiSigWalletWrapper extends BaseWrapper {

    @Value("${blockchain.contracts.multiSigWallet}")
    private String contractAddress;

    private MultiSigWallet contract;

    public MultiSigWalletWrapper(Web3j web3j, TransactionManager transactionManager,
                                 ContractGasProvider gasProvider) {
        super(web3j, transactionManager, gasProvider);
    }

    @PostConstruct
    public void init() {
        contract = MultiSigWallet.load(contractAddress, web3j, transactionManager, gasProvider);
        log.info("MultiSigWallet contract loaded: {}", contractAddress);
    }

    // 提供给 Service 层获取合约地址
    public String getContractAddress() {
        return contractAddress;
    }

    /**
     * 创建仲裁提案
     */
    public String createProposal(BigInteger tradeId, String accused, String victim, BigInteger amount, String reason) throws Exception {
        TransactionReceipt receipt = contract.createProposal(tradeId, accused, victim, amount, reason)
                .send();
        log.info("CreateProposal Tx Hash: {}", receipt.getTransactionHash());
        return receipt.getTransactionHash();
    }

    /**
     * 投票
     */
    public String voteProposal(BigInteger proposalId, boolean support) throws Exception {
        return contract.voteProposal(proposalId, support)
                .send()
                .getTransactionHash();
    }

    /**
     * 终结过期提案
     */
    public String finalizeProposal(BigInteger proposalId) throws Exception {
        return contract.finalizeProposal(proposalId)
                .send()
                .getTransactionHash();
    }

    /**
     * 获取提案详情
     */
    public ProposalInfo getProposalDetails(BigInteger proposalId) throws Exception {
        // 对应合约中 getProposalDetails 的返回值
        Tuple10<BigInteger, String, String, BigInteger, String, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger> result = contract.getProposalDetails(proposalId).send();


        ProposalInfo info = new ProposalInfo();
        info.tradeId = result.component1();
        info.accusedParty = result.component2();
        info.victimParty = result.component3();
        info.compensationAmount = result.component4();
        info.reason = result.component5();
        info.voteCount = result.component6();
        info.rejectCount = result.component7();
        info.status = result.component8().intValue();
        info.createdAt = result.component9();
        info.deadline = result.component10();

        return info;
    }

    /**
     * 检查是否为委员
     */
    public boolean isCommitteeMember(String address) throws Exception {
        return contract.isCommitteeMember(address).send();
    }

    /**
     * 获取委员会成员列表
     */
    public List<String> getCommitteeMembers() throws Exception {
        return contract.getCommitteeMembers().send();
    }

    /**
     * 获取提案总数
     */
    public BigInteger getProposalCount() throws Exception {
        return contract.proposalCount().send();
    }

    /**
     * 从交易回执中解析 ProposalCreated 事件的 ID
     */
    public BigInteger getProposalIdFromReceipt(TransactionReceipt receipt) {
        List<MultiSigWallet.ProposalCreatedEventResponse> events = MultiSigWallet.getProposalCreatedEvents(receipt);
        if (!events.isEmpty()) {
            return events.get(0).proposalId;
        }
        return null;
    }

    /**
     * 监听 ProposalCreated 事件
     */
    public Flowable<MultiSigWallet.ProposalCreatedEventResponse> proposalCreatedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.proposalCreatedEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 ProposalVoted 事件
     */
    public Flowable<MultiSigWallet.VoteCastEventResponse> proposalVotedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.voteCastEventFlowable(startBlock, endBlock);
    }

    /**
     * 监听 ProposalExecuted 事件
     */
    public Flowable<MultiSigWallet.ProposalExecutedEventResponse> proposalExecutedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.proposalExecutedEventFlowable(startBlock, endBlock);
    }

    // 监听 CommitteeMemberAdded 事件
    public Flowable<MultiSigWallet.CommitteeMemberAddedEventResponse> committeeMemberAddedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.committeeMemberAddedEventFlowable(startBlock, endBlock);
    }

    // 监听 CommitteeMemberRemoved 事件
    public Flowable<MultiSigWallet.CommitteeMemberRemovedEventResponse> committeeMemberRemovedEventFlowable(
            DefaultBlockParameterName startBlock,
            DefaultBlockParameterName endBlock) {
        return contract.committeeMemberRemovedEventFlowable(startBlock, endBlock);
    }


    @Data
    public static class ProposalInfo {
        public BigInteger tradeId;
        public String accusedParty;
        public String victimParty;
        public BigInteger compensationAmount;
        public String reason;
        public BigInteger voteCount;
        public BigInteger rejectCount;
        public int status;
        public BigInteger createdAt;
        public BigInteger deadline;
    }
}
