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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketService {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 用户上线处理
     */
    public void userOnline(String address) {
        log.info("User online: {}", address);

        try {
            // 发送欢迎通知
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
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle message: {}", e.getMessage());
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
}