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

    /**
     * 请求交易匹配
     */
    @PostMapping("/request-match")
    public JsonVO<TradeRequestDTO> requestMatch(
            @CurrentUser String address,
            @RequestBody TradeRequestParam param) {
        return JsonVO.success(tradeService.requestMatch(address, param));
    }

    /**
     * 创建交易对
     */
    @PostMapping("/create-pair")
    public JsonVO<TradeDTO> createTradePair(@RequestBody TradeMatchDTO match) {
        return JsonVO.success(tradeService.createTradePair(match));
    }

    /**
     * 率先转账方 (Party A) 确认
     */
    @PostMapping("/confirm/party-a")
    public JsonVO<TradeDTO> confirmPartyA(
            @CurrentUser String address,
            String tradeId, String txHash) {
        return JsonVO.success(tradeService.confirmPartyA(address, tradeId, txHash));
    }

    /**
     * 履约方 (Party B) 确认
     */
    @PostMapping("/confirm/party-b")
    public JsonVO<TradeDTO> confirmPartyB(
            @CurrentUser String address,
            String tradeId, String txHash) {
        return JsonVO.success(tradeService.confirmPartyB(address, tradeId, txHash));
    }

    /**
     * 甲方最终确认，完成交易
     */
    @PostMapping("/final-confirm")
    public JsonVO<TradeDTO> finalConfirmPartyA(
            @CurrentUser String address,
            @RequestParam String tradeId) {
        return JsonVO.success(tradeService.finalConfirmPartyA(address, tradeId));
    }

    /**
     * 发起争议
     */
    @PostMapping("/dispute")
    public JsonVO<DisputeDTO> disputeTrade(
            @CurrentUser String address,
            @RequestBody DisputeParam param) {
        return JsonVO.success(tradeService.disputeTrade(address, param));
    }

    /**
     * 处理争议（仲裁委员会）
     */
    @PostMapping("/dispute/resolve")
    public JsonVO<DisputeDTO> resolveDispute(
            @RequestBody ResolveDisputeParam param) {
        // 这里可以从 token 或 session 获取管理员身份
        return JsonVO.success(tradeService.resolveDispute(param.getDisputeId(), param));
    }

    /**
     * 查询交易详情
     */
    @GetMapping("/detail/{tradeId}")
    public JsonVO<TradeDTO> getTradeDetail(@PathVariable String tradeId) {
        return JsonVO.success(tradeService.getTradeDetail(tradeId));
    }

    /**
     * 查询用户交易列表
     */
    @GetMapping("/list")
    public JsonVO<List<TradeDTO>> getUserTrades(
            @CurrentUser String address,
            @RequestParam(required = false) Integer status) {
        return JsonVO.success(tradeService.getUserTrades(address, status));
    }
}
