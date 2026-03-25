package com.mnnu.component.handler;
/**
 * WebSocket服务
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.component.handler.CustomWebSocketHandler;
import com.mnnu.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {


    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;

    /**
     * 用户上线处理
     * 关键：推送离线消息 + 清除 Redis 存储
     */
    public void userOnline(String address) {
        log.info("User online: {}", address);

        try {
            // 1. 更新 Redis 中的在线状态
            updateOnlineStatus(address, true);

            // 2. 推送未读消息
            pushOfflineMessages(address);

            // 3. 发送欢迎通知
            sendWelcomeNotification(address);

        } catch (Exception e) {
            log.error("Failed to handle user online: {}", e.getMessage());
        }
    }


    /**
     * 用户离线处理
     */
    public void userOffline(String address) {
        log.info("User offline: {}", address);

        try {
            // 1. 更新 Redis 中的在线状态
            updateOnlineStatus(address, false);

            // 2. 记录离线时间
            recordOfflineTime(address);

        } catch (Exception e) {
            log.error("Failed to handle user offline: {}", e.getMessage());
        }
    }

    /**
     * 处理接收到的消息
     */
    public void handleMessage(String address, String payload) {
        try {
            // 解析消息
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);
            String type = (String) message.get("type");

            log.info("Handling {} message from {}: {}", type, address, payload);

            // 根据消息类型分发处理
            switch (type) {
                case "heartbeat":
                    handleHeartbeat(address);
                    break;
                case "subscribe_trade":
                    handleTradeSubscribe(address, message);
                    break;
                case "subscribe_rate":
                    handleRateSubscribe(address, message);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle message: {}", e.getMessage());
        }
    }

    /**
     * 更新用户在线状态
     */
    private void updateOnlineStatus(String address, boolean online) {
        try {
            // 通过 Redis 存储在线状态
            redisTemplate.opsForValue().set("user:online:" + address, String.valueOf(online), 30, TimeUnit.MINUTES);
            log.debug("User {} online status updated: {}", address, online);
        } catch (Exception e) {
            log.warn("Failed to update online status: {}", e.getMessage());
        }
    }

    /**
     * 推送离线消息
     * 关键：推送成功后立即清除 Redis 存储
     */
    private void pushOfflineMessages(String address) {
        try {
            // 1. 从 Redis 获取用户的未读消息
            List<NotificationDTO> offlineMessages = notificationService.getOfflineMessages(address);

            if (!offlineMessages.isEmpty()) {
                log.info("Pushing {} offline messages to user {}", offlineMessages.size(), address);

                // 2. 逐条推送
                for (NotificationDTO notification : offlineMessages) {
                    pushNotification(address, notification);
                }

                // 3. ✅ 推送成功后清除 Redis 存储
                notificationService.clearOfflineMessages(address);

                log.info("Successfully pushed and cleared offline messages for user {}", address);
            }
        } catch (Exception e) {
            log.warn("Failed to push offline messages: {}", e.getMessage());
        }
    }


    /**
     * 发送欢迎通知
     */
    private void sendWelcomeNotification(String address) {
        try {
            NotificationDTO notification = new NotificationDTO();
            notification.setAddress(address);
            notification.setTitle("Welcome");
            notification.setContent("Welcome back!");
            notification.setType(0); // 系统通知类型
            notification.setIsRead(false);
            notification.setCreateTime(LocalDateTime.now());

            pushNotification(address, notification);
        } catch (Exception e) {
            log.warn("Failed to send welcome notification: {}", e.getMessage());
        }
    }


    /**
     * 记录离线时间
     */
    private void recordOfflineTime(String address) {
        try {
            // 记录用户离线时间到 Redis
            redisTemplate.opsForValue().set("user:last_offline:" + address,
                    String.valueOf(System.currentTimeMillis()), 7, TimeUnit.DAYS);
            log.debug("Recorded offline time for user {}", address);
        } catch (Exception e) {
            log.warn("Failed to record offline time: {}", e.getMessage());
        }
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeat(String address) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", "pong");
            response.put("timestamp", System.currentTimeMillis());

            String message = objectMapper.writeValueAsString(response);
            CustomWebSocketHandler.sendToUser(address, message);

            log.debug("Heartbeat responded for user {}", address);
        } catch (Exception e) {
            log.warn("Failed to handle heartbeat: {}", e.getMessage());
        }
    }

    /**
     * 处理交易订阅
     */
    private void handleTradeSubscribe(String address, Map<String, Object> message) {
        try {
            String tradeId = (String) message.get("tradeId");
            if (tradeId != null) {
                // 将用户添加到交易订阅列表
                redisTemplate.opsForSet().add("trade:subscribers:" + tradeId, address);
                log.info("User {} subscribed to trade {}", address, tradeId);
            }
        } catch (Exception e) {
            log.warn("Failed to handle trade subscription: {}", e.getMessage());
        }
    }

    /**
     * 处理汇率订阅
     */
    private void handleRateSubscribe(String address, Map<String, Object> message) {
        try {
            String pair = (String) message.get("pair");
            if (pair != null) {
                // 将用户添加到汇率订阅列表
                redisTemplate.opsForSet().add("rate:subscribers:" + pair, address);
                log.info("User {} subscribed to rate {}", address, pair);
            }
        } catch (Exception e) {
            log.warn("Failed to handle rate subscription: {}", e.getMessage());
        }
    }

    /**
     * 推送通知
     */
    public void pushNotification(String address, NotificationDTO notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            CustomWebSocketHandler.sendToUser(address, message);
            log.debug("Notification pushed to user {}: {}", address, notification.getTitle());
        } catch (Exception e) {
            log.error("Failed to push notification: {}", e.getMessage());
        }
    }

    /**
     * 推送交易状态更新
     */
    public void pushTradeUpdate(String address, String tradeId, String status) {
        try {
            Map<String, String> update = new HashMap<>();
            update.put("type", "trade_update");
            update.put("tradeId", tradeId);
            update.put("status", status);

            String message = objectMapper.writeValueAsString(update);
            CustomWebSocketHandler.sendToUser(address, message);
            log.debug("Trade update pushed to user {}: {} - {}", address, tradeId, status);
        } catch (Exception e) {
            log.error("Failed to push trade update: {}", e.getMessage());
        }
    }

    /**
     * 推送汇率更新
     */
    public void pushRateUpdate(String pair, String rate) {
        try {
            Map<String, String> update = new HashMap<>();
            update.put("type", "rate_update");
            update.put("pair", pair);
            update.put("rate", rate);

            String message = objectMapper.writeValueAsString(update);
            CustomWebSocketHandler.broadcast(message);
            log.debug("Rate update broadcast: {} = {}", pair, rate);
        } catch (Exception e) {
            log.error("Failed to push rate update: {}", e.getMessage());
        }
    }
}