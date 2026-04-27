package com.mnnu.component.listener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.TradeDTO;
import com.mnnu.dto.TradeMatchDTO;
import com.mnnu.service.MatchingEngineService;
import com.mnnu.service.NotificationService;
import com.mnnu.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeMatchListener {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TradeService tradeService;
    @RabbitListener(queues = SystemConstants.MQQueue.TRADE_MATCH)
    public void handleTradeMatch(String message) {
        log.info("Received trade match event: {}", message);

        try {
            List<TradeMatchDTO> matches = objectMapper.readValue(message, new TypeReference<>() {
            });

            for (TradeMatchDTO match : matches) {
                    try {
                        TradeDTO trade = tradeService.createTradePair(match);
                        log.info("Trade record created in DB: tradeId={}, partyA={}, partyB={}",
                                trade.getTradeId(), match.getPartyA(), match.getPartyB());

                        // 通知 Party A - 匹配成功，准备创建交易
                        if (match.getPartyA() != null && !match.getPartyA().isEmpty()) {
                            String content = String.format("您与 %s 的交易匹配成功！金额: %s %s → %s。请在钱包中签名以创建链上交易对。",
                                    match.getPartyB(),
                                    match.getAmount(),
                                    match.getFromCurrency(),
                                    match.getToCurrency());
                            notificationService.sendSystemNotification(match.getPartyA(), "交易匹配成功", content);
                            log.info("Sent match notification to Party A: {}", match.getPartyA());
                        }

                        // 通知 Party B - 匹配成功，准备创建交易
                        if (match.getPartyB() != null && !match.getPartyB().isEmpty()) {
                            String content = String.format("您与 %s 的交易匹配成功！金额: %s %s → %s。请在钱包中签名以创建链上交易对。",
                                    match.getPartyA(),
                                    match.getAmount(),
                                    match.getFromCurrency(),
                                    match.getToCurrency());
                            notificationService.sendSystemNotification(match.getPartyB(), "交易匹配成功", content);
                            log.info("Sent match notification to Party B: {}", match.getPartyB());
                        }
                    } catch (Exception e) {
                        log.error("Failed to send match notification", e);
                    }
            }
        } catch (Exception e) {
            log.error("Error processing trade match event", e);
        }
    }
}