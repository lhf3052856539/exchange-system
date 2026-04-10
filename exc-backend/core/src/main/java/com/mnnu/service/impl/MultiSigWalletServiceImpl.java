package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.DisputeParam;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.CommitteeMemberEntity;
import com.mnnu.entity.DisputeRecordEntity;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.exception.BusinessException;
import com.mnnu.mapper.CommitteeMemberMapper;
import com.mnnu.mapper.DisputeRecordMapper;
import com.mnnu.mapper.TradeMapper;
import com.mnnu.service.MultiSigWalletService;
import com.mnnu.wrapper.MultiSigWalletWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSigWalletServiceImpl implements MultiSigWalletService {

    private final MultiSigWalletWrapper multiSigWalletWrapper;
    @Autowired
    private TradeMapper tradeMapper;
    @Autowired
    private DisputeRecordMapper disputeRecordMapper;
    @Autowired
    private CommitteeMemberMapper committeeMemberMapper;



    @Override
    public String getMultiSigWalletAddress() {
        return multiSigWalletWrapper.getContractAddress();
    }

    @Override
    public List<String> getCommitteeMembers() {
        // 从链下数据库获取当前仲裁委员会成员
        List<CommitteeMemberEntity> members = committeeMemberMapper.selectList(
                new QueryWrapper<CommitteeMemberEntity>().eq("is_active", 1)
        );
        return members.stream()
                .map(CommitteeMemberEntity::getAddress)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isCommitteeMember(String address) {
        // 快速校验数据库中是否存在该活跃成员
        Long count = committeeMemberMapper.selectCount(
                new QueryWrapper<CommitteeMemberEntity>()
                        .eq("address", address)
                        .eq("is_active", 1)
        );
        return count > 0;
    }

    @Override
    public void createArbitrationProposal(String creatorAddress, DisputeParam param) {
        // 权限校验
        if (!isCommitteeMember(creatorAddress)) {
            throw new BusinessException("Only committee members can create proposals");
        }

        // 业务数据校验
        TradeRecordEntity trade = tradeMapper.selectOne(new QueryWrapper<TradeRecordEntity>().eq("trade_id", param.getTradeId()));
        if (trade == null) throw new BusinessException("Trade not found");

        DisputeRecordEntity dispute = disputeRecordMapper.selectOne(new QueryWrapper<DisputeRecordEntity>().eq("trade_id", param.getTradeId()));
        if (dispute == null) throw new BusinessException("Dispute record not found");


    }

    @Override
    public void voteArbitrationProposal(String voterAddress, BigInteger proposalId, boolean support) {
        // 权限校验
        if (!isCommitteeMember(voterAddress)) {
            throw new BusinessException("Only committee members can vote");
        }

    }

    /**
     * @notice 终结提案：处理超时未通过的提案
     */
    @Override
    public String finalizeProposal(BigInteger proposalId) {
        try {
            log.info("Finalizing proposal {}", proposalId);
            return multiSigWalletWrapper.finalizeProposal(proposalId);
        } catch (Exception e) {
            log.error("Failed to finalize proposal {}", proposalId, e);
            throw new BusinessException("终结提案失败: " + e.getMessage());
        }
    }

    @Override
    public DisputeDTO getProposalDetails(BigInteger proposalId) {
        // 先查数据库
        DisputeRecordEntity entity = disputeRecordMapper.selectOne(
                new QueryWrapper<DisputeRecordEntity>().eq("proposal_id", proposalId.toString())
        );

        if (entity != null) {
            return convertToDTO(entity);
        }

        // 数据库没有，再查链上（兜底）
        try {
            MultiSigWalletWrapper.ProposalInfo info = multiSigWalletWrapper.getProposalDetails(proposalId);
            return convertToDTO(info);
        } catch (Exception e) {
            throw new BusinessException("获取提案详情失败");
        }
    }

    @Override
    public List<DisputeRecordEntity> getPendingDisputes() {
        // 查询 proposal_status 为 0 (Pending) 的记录
        return disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>()
                        .eq("proposal_status", 0)
                        .orderByDesc("create_time")
        );
    }

    @Override
    public List<DisputeRecordEntity> getPendingProposals() {
        // 直接从数据库查状态为 0 (Pending) 的记录
        List<DisputeRecordEntity> list = disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>().eq("proposal_status", 0)
        );
        return list;
    }

    @Override
    public List<DisputeRecordEntity> getHistoryProposals() {
        // 直接从数据库查状态为 1 (Executed) 或 2 (Rejected) 的记录
        List<DisputeRecordEntity> list = disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>()
                        .in("proposal_status", 1, 2)
                        .orderByDesc("update_time")
        );
        return list;
    }

    private DisputeDTO convertToDTO(DisputeRecordEntity entity) {
        if (entity == null) return null;

        DisputeDTO dto = new DisputeDTO();
        dto.setTradeId(entity.getTradeId());
        dto.setProposalId(entity.getProposalId());
        dto.setInitiator(entity.getInitiator());
        dto.setAccused(entity.getAccused());
        dto.setCompensationAmount(entity.getCompensationAmount());
        dto.setReason(entity.getReason());
        dto.setStatus(entity.getProposalStatus());
        dto.setCreateTime(entity.getCreateTime());

        return dto;
    }

    private DisputeDTO convertToDTO(MultiSigWalletWrapper.ProposalInfo info) {
        if (info == null) return null;

        DisputeDTO dto = new DisputeDTO();
        dto.setTradeId(info.tradeId.toString());
        dto.setInitiator(info.accusedParty);
        dto.setAccused(info.victimParty);
        dto.setCompensationAmount(new BigDecimal(info.getCompensationAmount()));
        dto.setReason(info.reason);
        dto.setStatus(info.status);

        return dto;
    }

}
