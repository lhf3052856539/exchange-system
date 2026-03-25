package com.mnnu.controller;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.*;
import com.mnnu.service.TradeService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/apis/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

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
