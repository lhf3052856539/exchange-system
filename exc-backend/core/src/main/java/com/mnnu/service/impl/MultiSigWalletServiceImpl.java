package com.mnnu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.DisputeParam;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import static com.mnnu.constant.SystemConstants.RedisKey.*;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiSigWalletServiceImpl implements MultiSigWalletService {
    @Autowired
    private MultiSigWalletWrapper multiSigWalletWrapper;
    @Autowired
    private RedissonClient redissonClient;

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
    @Transactional(rollbackFor = Exception.class)
    public void createArbitrationProposal(String creatorAddress, DisputeParam param) {
        RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + param.getChainTradeId());
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            // 权限校验
            if (!isCommitteeMember(creatorAddress)) {
                throw new BusinessException("Only committee members can create proposals");
            }

            // 业务数据校验
            TradeRecordEntity trade = tradeMapper.selectOne(new QueryWrapper<TradeRecordEntity>().eq("chain_trade_id", param.getChainTradeId()));
            if (trade == null) throw new BusinessException("Trade not found");

            DisputeRecordEntity dispute = disputeRecordMapper.selectOne(new QueryWrapper<DisputeRecordEntity>().eq("chain_trade_id", param.getChainTradeId()));
            if (dispute == null) throw new BusinessException("Dispute record not found");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voteArbitrationProposal(String voterAddress, BigInteger proposalId, boolean support) {
        RLock lock = redissonClient.getLock(ARBITRATION_LOCK_KEY_PREFIX + proposalId);
        try {
            if (!lock.tryLock(10, 30, SECONDS)) {
                throw new BusinessException("System busy, try again");
            }

            // 权限校验
            if (!isCommitteeMember(voterAddress)) {
                throw new BusinessException("Only committee members can vote");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "System error");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     *终结提案：处理超时未通过的提案
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
        return disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>()
                        .eq("proposal_status", 0)
                        .isNull("proposal_id")
                        .orderByDesc("create_time")
        );
    }

    @Override
    public List<DisputeRecordEntity> getPendingProposals() {
        return disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>()
                        .in("proposal_status", 0,1)
                        .isNotNull("proposal_id")
                        .orderByDesc("create_time")
        );
    }

    @Override
    public List<DisputeRecordEntity> getHistoryProposals() {
        return disputeRecordMapper.selectList(
                new QueryWrapper<DisputeRecordEntity>()
                        .in("proposal_status", 2, 3, 4)
                        .orderByDesc("create_time")
        );
    }

    private DisputeDTO convertToDTO(DisputeRecordEntity entity) {
        if (entity == null) return null;

        DisputeDTO dto = new DisputeDTO();
        dto.setChainTradeId(entity.getChainTradeId());
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
        dto.setChainTradeId(info.tradeId.toString());
        dto.setInitiator(info.accusedParty);
        dto.setAccused(info.victimParty);
        dto.setCompensationAmount(new BigDecimal(info.compensationAmount).divide(new BigDecimal("1000000"), 6, BigDecimal.ROUND_HALF_UP));
        dto.setReason(info.reason);
        dto.setStatus(info.status);

        return dto;
    }

}
