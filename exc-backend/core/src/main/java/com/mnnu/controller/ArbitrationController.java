package com.mnnu.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.ArbitrationProposalParam;
import com.mnnu.dto.DisputeDTO;
import com.mnnu.dto.DisputeParam;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.entity.DisputeRecordEntity;
import com.mnnu.entity.TradeRecordEntity;
import com.mnnu.mapper.DisputeRecordMapper;
import com.mnnu.mapper.TradeMapper;
import com.mnnu.service.MultiSigWalletService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;
import java.time.LocalDateTime;
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
    private final TradeMapper tradeMapper;
    private final Web3j web3j;

    /**
     * 创建仲裁提案
     */
    @PostMapping("/proposal/create")
    public JsonVO<Void> createProposal(
            @CurrentUser String address,
            @RequestBody DisputeParam param) {
        multiSigWalletService.createArbitrationProposal(address, param);
        return JsonVO.success();
    }

    /**
     * 对仲裁提案投票
     */
    @PostMapping("/proposal/vote")
    public JsonVO<Void> voteProposal(
            @CurrentUser String address,
            @RequestParam Long proposalId,
            @RequestParam Boolean support) {
        multiSigWalletService.voteArbitrationProposal(address, BigInteger.valueOf(proposalId), support);
        return JsonVO.success();
    }


    /**
     * 获取提案详情
     */
    @GetMapping("/proposal/{proposalId}")
    public JsonVO<DisputeDTO> getProposal(@PathVariable Long proposalId) {
        return JsonVO.success(multiSigWalletService.getProposalDetails(BigInteger.valueOf(proposalId)));
    }

    /**
     * 获取仲裁委员会成员列表
     */
    @GetMapping("/committee")
    public JsonVO<List<String>> getCommitteeMembers() {
        return JsonVO.success(multiSigWalletService.getCommitteeMembers());
    }

    /**
     * 检查当前用户是否为委员会成员
     */
    @GetMapping("/check-committee")
    public JsonVO<Boolean> checkCommitteeMember(@CurrentUser String address) {
        return JsonVO.success(multiSigWalletService.isCommitteeMember(address));
    }

    /**
     * 获取待处理的仲裁提案
     */
    @GetMapping("/proposal/pending")
    public JsonVO<List<DisputeRecordEntity>> getPendingProposals() {
        return JsonVO.success(multiSigWalletService.getPendingProposals());
    }

    /**
     * 获取待处理的争议列表
     */
    @GetMapping("/dispute/pending")
    public JsonVO<List<DisputeRecordEntity>> getPendingDisputes() {
        return JsonVO.success(multiSigWalletService.getPendingDisputes());
    }

    /**
     * 获取仲裁历史
     */
    @GetMapping("/proposal/history")
    public JsonVO<List<DisputeRecordEntity>> getHistoryProposals() {
        return JsonVO.success(multiSigWalletService.getHistoryProposals());
    }

}