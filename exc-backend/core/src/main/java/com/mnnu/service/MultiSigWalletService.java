package com.mnnu.service;

import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.DisputeParam;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.DisputeRecordEntity;

import java.math.BigInteger;
import java.util.List;

public interface MultiSigWalletService {


    String getMultiSigWalletAddress();

    List<String> getCommitteeMembers();

    /**
     * 创建仲裁提案（包含业务校验）
     */
    void createArbitrationProposal(String creatorAddress, DisputeParam param);

    /**
     * 投票（包含业务校验）
     */
    void voteArbitrationProposal(String voterAddress, BigInteger proposalId, boolean support);


    String finalizeProposal(BigInteger proposalId);

    DisputeDTO getProposalDetails(BigInteger proposalId);

    boolean isCommitteeMember(String address);

    /**
     * 获取待处理的争议列表（对应链上 Pending 状态的提案）
     */
    List<DisputeRecordEntity> getPendingDisputes();

    List<DisputeRecordEntity> getPendingProposals();

    List<DisputeRecordEntity> getHistoryProposals();


}
