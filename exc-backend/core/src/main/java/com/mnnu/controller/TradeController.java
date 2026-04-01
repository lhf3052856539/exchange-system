package com.mnnu.controller;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.*;
import com.mnnu.service.MultiSigWalletService;
import com.mnnu.service.TradeService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/apis/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;
    private final MultiSigWalletService multiSigWalletService;

    @PostMapping("/request-match")
    public JsonVO<TradeRequestDTO> requestMatch(
            @CurrentUser String address,
            @RequestBody TradeRequestParam param) {
        return JsonVO.success(tradeService.requestMatch(address, param));
    }

    @PostMapping("/create-pair")
    public JsonVO<TradeDTO> createTradePair(@RequestBody TradeMatchDTO match) {
        return JsonVO.success(tradeService.createTradePair(match));
    }

    @PostMapping("/confirm-party-a")
    public JsonVO<TradeDTO> confirmPartyA(
            @CurrentUser String address,
            @RequestParam String tradeId,
            @RequestParam String txHash) {
        return JsonVO.success(tradeService.confirmPartyA(address, tradeId, txHash));
    }

    @PostMapping("/confirm-party-b")
    public JsonVO<TradeDTO> confirmPartyB(
            @CurrentUser String address,
            @RequestParam String tradeId,
            @RequestParam String txHash) {
        return JsonVO.success(tradeService.confirmPartyB(address, tradeId, txHash));
    }

    @PostMapping("/final-confirm-party-a")
    public JsonVO<TradeDTO> finalConfirmPartyA(
            @CurrentUser String address,
            @RequestParam String tradeId) {
        return JsonVO.success(tradeService.finalConfirmPartyA(address, tradeId));
    }

    @PostMapping("/dispute")
    public JsonVO<DisputeDTO> disputeTrade(
            @CurrentUser String address,
            @RequestBody DisputeParam param) {
        return JsonVO.success(tradeService.disputeTrade(address, param));
    }

    @PostMapping("/arbitration/create-proposal")
    public JsonVO<String> createArbitrationProposal(
            @CurrentUser String address,
            @RequestBody ArbitrationProposalParam param) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can create proposals");
        }

        String txHash = multiSigWalletService.createProposal(
                new BigInteger(param.getTradeId()),
                param.getAccusedParty(),
                param.getVictimParty(),
                new BigInteger(param.getCompensationAmount()),
                param.getReason()
        );

        return JsonVO.success(txHash);
    }

    @PostMapping("/arbitration/vote")
    public JsonVO<String> voteArbitrationProposal(
            @CurrentUser String address,
            @RequestParam Long proposalId,
            @RequestParam Boolean support) {

        if (!multiSigWalletService.isCommitteeMember(address)) {
            return JsonVO.error("Only committee members can vote");
        }

        String txHash = multiSigWalletService.voteProposal(
                BigInteger.valueOf(proposalId),
                support
        );

        return JsonVO.success(txHash);
    }

    @GetMapping("/arbitration/proposal/{proposalId}")
    public JsonVO<ProposalDTO> getArbitrationProposal(@PathVariable Long proposalId) {
        return JsonVO.success(multiSigWalletService.getProposalDetails(BigInteger.valueOf(proposalId)));
    }

    @GetMapping("/arbitration/committee")
    public JsonVO<List<String>> getCommitteeMembers() {
        return JsonVO.success(multiSigWalletService.getCommitteeMembers());
    }


    @GetMapping("/detail/{tradeId}")
    public JsonVO<TradeDTO> getTradeDetail(@PathVariable String tradeId) {
        return JsonVO.success(tradeService.getTradeDetail(tradeId));
    }



    @GetMapping("/list")
    public JsonVO<List<TradeDTO>> getUserTrades(
            @CurrentUser String address,
            @RequestParam(required = false) Integer status) {
        return JsonVO.success(tradeService.getUserTrades(address, status));
    }
}
