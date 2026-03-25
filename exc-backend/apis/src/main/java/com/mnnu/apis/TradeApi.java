package com.mnnu.apis;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.*;
import com.mnnu.vo.JsonVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易 API 接口
 */

public interface TradeApi {


    JsonVO<TradeRequestDTO> requestMatch(
            @CurrentUser String address,
            @RequestBody TradeRequestParam param);


    JsonVO<TradeDTO> confirmPartyA(
            @CurrentUser String address,
            @RequestParam String tradeId,
            @RequestParam String txHash);


    JsonVO<TradeDTO> confirmPartyB(
            @CurrentUser String address,
            @RequestParam String tradeId,
            @RequestParam String txHash);


    JsonVO<DisputeDTO> disputeTrade(
            @CurrentUser String address,
            @RequestBody DisputeParam param);


    JsonVO<TradeDTO> getTradeDetail(@PathVariable String tradeId);


    JsonVO<List<TradeDTO>> getUserTrades(
            @CurrentUser String address,
            @RequestParam(required = false) Integer status);
}
