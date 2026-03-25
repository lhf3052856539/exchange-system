package com.mnnu.component.listener;
import com.mnnu.constant.SystemConstants;
import com.mnnu.dto.TradeMatchDTO;
import com.mnnu.service.MatchingEngineService;
import com.mnnu.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeMatchListener {

    private final MatchingEngineService matchingEngine;
    private final TradeService tradeService;

    @RabbitListener(queues = SystemConstants.MQQueue.TRADE_MATCH)
    public void handleTradeMatch(String message) {
        log.info("Received trade match request: {}", message);
        try {
            List<TradeMatchDTO> matches = matchingEngine.executeMatching();

            for (TradeMatchDTO match : matches) {
                try {
                    tradeService.createTradePair(match);
                    log.info("Trade pair created successfully for match: {} vs {}",
                            match.getPartyA(), match.getPartyB());
                } catch (Exception e) {
                    log.error("Failed to create trade pair: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute matching: {}", e.getMessage(), e);
        }
    }
}
