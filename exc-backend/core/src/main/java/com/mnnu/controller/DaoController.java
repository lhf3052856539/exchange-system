package com.mnnu.controller;

import com.mnnu.apis.DaoApi;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.CreateProposalDTO;
import com.mnnu.dto.PageDTO;
import com.mnnu.dto.ProposalDTO;
import com.mnnu.service.DaoService;
import com.mnnu.service.MultiSigWalletService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.Map;

/**
 * DAO治理控制器
 */
@Slf4j
@RestController
@RequestMapping("/apis/dao")
@RequiredArgsConstructor
public class DaoController implements DaoApi {

    private final DaoService daoService;
    private final MultiSigWalletService multiSigWalletService;


    @Override
    @PostMapping("/proposal/create")
    public JsonVO<Void> createProposal(
            @CurrentUser String address,
            @RequestBody CreateProposalDTO proposalDTO
    ) {
        log.info("Create proposal from address: {}", address);
        daoService.createProposal(proposalDTO,address);
        return JsonVO.success();
    }

    @Override
    @PostMapping("/proposal/vote")
    public JsonVO<Void> vote(
            @CurrentUser String address,
            @RequestParam BigInteger proposalId,
            @RequestParam Boolean support
    ) {
        log.info("Vote on proposal {} from address: {}", proposalId, address);
        daoService.vote(proposalId, support, address);
        return JsonVO.success();
    }

    @Override
    @PostMapping("/proposal/queue")
    public JsonVO<Void> queueProposal(@RequestParam BigInteger proposalId) {
        log.info("Queue proposal: {}", proposalId);
        daoService.queueProposal(proposalId);
        return JsonVO.success();
    }

    @Override
    @PostMapping("/proposal/execute")
    public JsonVO<Void> executeProposal(
            @RequestParam BigInteger proposalId,
            @RequestParam(required = false) BigInteger eta
    ) {
        log.info("Execute proposal: {}, eta (ignored): {}", proposalId);
        daoService.executeProposal(proposalId,eta);
        return JsonVO.success();
    }


    @Override
    @PostMapping("/proposal/cancel")
    public JsonVO<Void> cancelProposal(
            @CurrentUser String address,
            @RequestParam BigInteger proposalId
    ) {
        log.info("Cancel proposal {} from address: {}", proposalId, address);
        daoService.cancelProposal(proposalId, address);
        return JsonVO.success();
    }

    @Override
    @GetMapping("/proposal/detail")
    public JsonVO<ProposalDTO> getProposal(
            @RequestParam BigInteger proposalId,
            @RequestParam(required = false) String address
    ) {
        log.debug("Get proposal detail: {}", proposalId);
        ProposalDTO proposal = daoService.getProposal(proposalId, address);
        return JsonVO.success(proposal);
    }

    @Override
    @GetMapping("/proposal/list")
    public JsonVO<PageDTO<ProposalDTO>> getAllProposals(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String address
    ) {
        log.debug("Get all proposals page: {}, size: {}", pageNum, pageSize);
        PageDTO<ProposalDTO> proposals = daoService.getAllProposals(pageNum, pageSize, address);
        return JsonVO.success(proposals);
    }

    @Override
    @GetMapping("/proposal/state")
    public JsonVO<Integer> getProposalState(@RequestParam BigInteger proposalId) {
        log.debug("Get proposal state: {}", proposalId);
        Integer state = daoService.getProposalState(proposalId);
        return JsonVO.success(state);
    }

    @Override
    @GetMapping("/proposal/has-voted")
    public JsonVO<Boolean> hasVoted(
            @RequestParam BigInteger proposalId,
            @RequestParam String voter
    ) {
        log.debug("Check if voted: proposal={}, voter={}", proposalId, voter);
        Boolean hasVoted = daoService.hasVoted(proposalId, voter);
        return JsonVO.success(hasVoted);
    }

    @Override
    @GetMapping("/voting-period")
    public JsonVO<BigInteger> getVotingPeriod() {
        log.debug("Get voting period");
        BigInteger period = daoService.getVotingPeriod();
        return JsonVO.success(period);
    }

    @Override
    @PostMapping("/voting-period/set")
    public JsonVO<String> setVotingPeriod(@RequestParam BigInteger newPeriod) {
        log.info("Set voting period to: {}", newPeriod);
        String txHash = daoService.setVotingPeriod(newPeriod);
        return JsonVO.success(txHash);
    }

    @Override
    @GetMapping("/proposal-count")
    public JsonVO<BigInteger> getProposalCount() {
        log.debug("Get proposal count");
        BigInteger count = daoService.getProposalCount();
        return JsonVO.success(count);
    }

    @GetMapping("/treasure/balance")
    public JsonVO<Map<String, Object>> getTreasureBalance() {
        Map<String, Object> balance = daoService.getTreasureBalance();
        return JsonVO.success(balance);
    }

    @PostMapping("/rotate-committee-member")
    public JsonVO<String> rotateCommitteeMember(
            @CurrentUser String address,
            @RequestParam String oldMember,
            @RequestParam String newMember) {

        // 这里应该添加 DAO 投票逻辑，只有投票通过才能轮换
        // 简化处理，直接由 owner 调用

        try {
            // TODO: 实现 DAO 投票通过后轮换委员会成员的逻辑
            return JsonVO.error("Committee rotation requires DAO approval");
        } catch (Exception e) {
            return JsonVO.error(e.getMessage());
        }
    }
}
