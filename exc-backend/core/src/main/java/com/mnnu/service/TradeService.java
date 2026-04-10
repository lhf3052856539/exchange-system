package com.mnnu.service;
/**
 * 交易服务接口
 */

import com.mnnu.dto.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TradeService {

    /**
     * 请求交易匹配
     */
    TradeRequestDTO requestMatch(String address, TradeRequestParam param);

    /**
     * 创建交易对（由匹配引擎调用）
     */
    TradeDTO createTradePair(TradeMatchDTO match);

    /**
     * 率先转账方确认
     */
    TradeDTO confirmPartyA(String address, String tradeId, String txHash);

    /**
     * 履约方确认
     */
    TradeDTO confirmPartyB(String address, String tradeId, String txHash);

    /**
     * 甲方最终确认，完成交易
     */
    TradeDTO finalConfirmPartyA(String address, String tradeId);

    @Transactional(rollbackFor = Exception.class)
    TradeDTO cancelTrade(String address, String tradeId);

    /**
     * 发起争议
     */
    DisputeDTO disputeTrade(String address, DisputeParam param);

    /**
     * 处理争议（管理员）
     */
    DisputeDTO resolveDispute(Long disputeId, ResolveDisputeParam param);

    /**
     * 查询交易详情
     */
    TradeDTO getTradeDetail(String tradeId);

    /**
     * 查询用户交易列表
     */
    List<TradeDTO> getUserTrades(String address, Integer status);

    /**
     * 检查过期交易
     */
    void checkExpiredTrades();


}
