package com.mnnu.controller;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.ArbitrationProposalParam;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.DisputeRecordEntity;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.mapper.DisputeRecordMapper;
import com.mnnu.mapper.TradeRecordMapper;
import com.mnnu.service.MultiSigWalletService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/apis/arbitration")
@RequiredArgsConstructor
public class ArbitrationController {

    private final MultiSigWalletService multiSigWalletService;

    private final DisputeRecordMapper disputeMapper;
    private final TradeRecordMapper tradeMapper;
    private final Web3j web3j;

    /**
     * 创建仲裁提案
     */
    @PostMapping("/proposal/create")
    public JsonVO<String> createProposal(
            @CurrentUser String address,
            @RequestBody ArbitrationProposalParam param) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can create proposals");
        }

        try {
            // 从数据库中查询交易记录，获取 chainTradeId
            TradeRecordEntity trade = tradeMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TradeRecordEntity>()
                            .eq("trade_id", param.getTradeId())
            );

            if (trade == null) {
                return JsonVO.error("Trade not found");
            }

            // 🔥 查询对应的争议记录
            DisputeRecordEntity dispute = disputeMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DisputeRecordEntity>()
                            .eq("trade_id", param.getTradeId())
            );

            if (dispute == null) {
                return JsonVO.error("Dispute not found");
            }

            // 优先使用 chainTradeId，如果没有则使用数据库主键 ID
            BigInteger tradeIndex;
            if (trade.getChainTradeId() != null) {
                tradeIndex = BigInteger.valueOf(trade.getChainTradeId());
            } else {
                // 对于尚未上链的争议交易，使用数据库主键 ID
                tradeIndex = BigInteger.valueOf(trade.getId());
                log.warn("Trade {} has no chainTradeId, using database id: {}", param.getTradeId(), trade.getId());
            }

            String txHash = multiSigWalletService.createProposal(
                    tradeIndex,
                    param.getAccusedParty(),
                    param.getVictimParty(),
                    new BigInteger(param.getCompensationAmount()),
                    param.getReason()
            );

            // 🔥 更新争议记录：标记已创建提案
            dispute.setProposalTxHash(txHash);
            dispute.setStatus(1); // 1-已创建提案，从待处理列表移除
            disputeMapper.updateById(dispute);
            log.info("✅ Updated dispute {} status to 1 (proposal created), txHash: {}", dispute.getId(), txHash);

            return JsonVO.success(txHash);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 对仲裁提案投票
     */
    @PostMapping("/proposal/vote")
    public JsonVO<String> voteProposal(
            @CurrentUser String address,
            @RequestParam Long proposalId,
            @RequestParam Boolean support) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can vote");
        }

        try {
            String txHash = multiSigWalletService.voteProposal(
                    BigInteger.valueOf(proposalId),
                    support
            );

            return JsonVO.success(txHash);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 对仲裁提案投票（使用前端钱包签名）
     */
    @PostMapping("/proposal/vote-signed")
    public JsonVO<String> voteProposalWithSignedTx(
            @CurrentUser String address,
            @RequestBody Map<String, String> signedTx) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can vote");
        }

        try {
            String rawTx = signedTx.get("rawTransaction");
            if (rawTx == null || rawTx.isEmpty()) {
                return JsonVO.error("Missing signed transaction");
            }

            // 🔥 从 rawTx 中提取 proposalId（需要解析交易数据，这里简化处理）
            // 实际上我们不知道是哪个 proposal，需要从交易数据中解析
            // 但为了简化，我们先发送交易，然后从事件日志中获取

            // 发送已签名的交易到区块链
            String txHash = multiSigWalletService.sendRawTransaction(rawTx);

            // 🔥 异步更新争议记录的投票信息
            CompletableFuture.runAsync(() -> {
                try {
                    // 等待几秒让链上确认
                    Thread.sleep(3000);

                    // 🔥 从交易回执中解析 proposalId
                    // 方法：查询最近有事件日志的交易
                    var receipt = web3j.ethGetTransactionReceipt(txHash).send();
                    if (receipt.getTransactionReceipt().isPresent()) {
                        var receiptData = receipt.getTransactionReceipt().get();

                        // 🔥 遍历所有争议记录，找到匹配的 proposal
                        // 因为不知道具体是哪个 proposal，需要查询所有待处理提案
                        List<DisputeRecordEntity> allDisputes = disputeMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DisputeRecordEntity>()
                                        .isNotNull("proposal_id") // 已有 proposalId 的
                                        .orderByDesc("create_time")
                        );

                        // 遍历更新
                        for (DisputeRecordEntity dispute : allDisputes) {
                            try {
                                BigInteger proposalId = new BigInteger(dispute.getProposalId());
                                ProposalDTO proposal = multiSigWalletService.getProposalDetails(proposalId);

                                // 🔥 更新投票信息
                                dispute.setVoteCount(proposal.getVoteCount() != null ? proposal.getVoteCount().intValue() : 0);
                                dispute.setDeadline(proposal.getDeadline() != null ? proposal.getDeadline().longValue() : null);

                                // 🔥 如果已执行或已拒绝，更新状态
                                if (Boolean.TRUE.equals(proposal.getExecuted())) {
                                    dispute.setExecuted(true);
                                    dispute.setStatus(1); // 已执行
                                } else if (Boolean.TRUE.equals(proposal.getRejected())) {
                                    dispute.setRejected(true);
                                    dispute.setStatus(2); // 已驳回
                                }

                                dispute.setUpdateTime(LocalDateTime.now());
                                disputeMapper.updateById(dispute);
                                log.info("✅ Updated dispute {} after vote: voteCount={}, executed={}, rejected={}",
                                        dispute.getId(), dispute.getVoteCount(), dispute.getExecuted(), dispute.getRejected());
                            } catch (Exception e) {
                                log.warn("⚠️ Failed to update dispute {}: {}", dispute.getProposalId(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to update disputes after voting: {}", e.getMessage());
                }
            });

            return JsonVO.success(txHash);
        } catch (Exception e) {
            log.error("Failed to send signed transaction", e);
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 获取提案详情
     */
    @GetMapping("/proposal/{proposalId}")
    public JsonVO<ProposalDTO> getProposal(@PathVariable Long proposalId) {
        try {
            if (proposalId == null || proposalId < 0) {
                return JsonVO.error("Invalid proposal ID");
            }
            ProposalDTO details = multiSigWalletService.getProposalDetails(BigInteger.valueOf(proposalId));
            return JsonVO.success(details);
        } catch (Exception e) {
            log.error("Failed to get proposal details", e);
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 获取仲裁委员会成员列表
     */
    @GetMapping("/committee")
    public JsonVO<List<String>> getCommitteeMembers() {
        try {
            List<String> members = multiSigWalletService.getCommitteeMembers();
            return JsonVO.success(members);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 检查当前用户是否为委员会成员
     */
    @GetMapping("/check-committee")
    public JsonVO<Boolean> checkCommitteeMember(@CurrentUser String address) {
        boolean isMember = multiSigWalletService.isCommitteeMember(address);
        return JsonVO.success(isMember);
    }

    /**
     * 获取待处理的仲裁提案
     */
    @GetMapping("/proposal/pending")
    public JsonVO<List<ProposalDTO>> getPendingProposals() {
        try {
            List<ProposalDTO> pendingList = multiSigWalletService.getPendingProposals();
            return JsonVO.success(pendingList);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 获取待处理的争议列表
     */
    @GetMapping("/dispute/pending")
    public JsonVO<List<DisputeDTO>> getPendingDisputes() {
        try {
            // 🔥 查询待处理争议：status=0 且没有 proposalId（未创建提案）
            List<DisputeRecordEntity> pendingDisputes = disputeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DisputeRecordEntity>()
                            .eq("status", 0)
                            .isNull("proposal_id") // 🔥 只返回未创建提案的争议
                            .orderByDesc("create_time")
            );

            List<DisputeDTO> dtoList = pendingDisputes.stream()
                    .map(entity -> {
                        DisputeDTO dto = new DisputeDTO();
                        dto.setId(entity.getId());
                        dto.setTradeId(entity.getTradeId());
                        dto.setInitiator(entity.getInitiator());
                        dto.setAccused(entity.getAccused());
                        dto.setReason(entity.getReason());
                        dto.setEvidence(entity.getEvidence());
                        dto.setStatus(entity.getStatus());
                        dto.setCreateTime(entity.getCreateTime());
                        return dto;
                    })
                    .collect(Collectors.toList());

            return JsonVO.success(dtoList);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 获取仲裁历史
     */
    @GetMapping("/proposal/history")
    public JsonVO<List<ProposalDTO>> getHistoryProposals() {
        try {
            // 🔥 从链上获取已执行或已拒绝的提案
            List<ProposalDTO> historyList = multiSigWalletService.getHistoryProposals();
            return JsonVO.success(historyList);
        } catch (Exception e) {
            log.error("Failed to get history proposals", e);
            return JsonVO.error(e.getMessage());
        }
    }

    /**
     * 执行仲裁提案
     */
    @PostMapping("/proposal/execute")
    public JsonVO<String> executeProposal(
            @CurrentUser String address,
            @RequestParam Long proposalId) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can execute proposals");
        }

        try {
            String txHash = multiSigWalletService.executeProposal(BigInteger.valueOf(proposalId));
            return JsonVO.success(txHash);
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }
}