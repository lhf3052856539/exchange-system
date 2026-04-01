package com.mnnu.service;

import com.mnnu.dto.ProposalDTO;

import java.math.BigInteger;
import java.util.List;

public interface MultiSigWalletService {

    String getMultiSigWalletAddress();

    List<String> getCommitteeMembers();

    String createProposal(
            BigInteger tradeId,
            String accusedParty,
            String victimParty,
            BigInteger compensationAmount,
            String reason
    );

    String voteProposal(BigInteger proposalId, boolean support);

    String executeProposal(BigInteger proposalId);
    /**
     * 发送已签名的原始交易
     */
    String sendRawTransaction(String signedTxHex);

    ProposalDTO getProposalDetails(BigInteger proposalId);

    boolean isCommitteeMember(String address);

    /**
     * 获取提案总数
     */
    BigInteger getProposalCount();

    /**
     * 获取所有待处理的提案（未执行且未拒绝）
     */
    List<ProposalDTO> getPendingProposals();

}
