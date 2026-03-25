package com.mnnu.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mnnu.component.handler.CustomWebSocketHandler;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.entity.NotificationEntity;
import com.mnnu.mapper.NotificationMapper;
import com.mnnu.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationMapper notificationMapper;

    /**
     * 发送通知（统一入口）
     * 策略：在线 → WebSocket 实时推送
     *      离线 → Redis 存储，等用户上线后推送
     */
    @Override
    public void sendNotification(NotificationDTO notification) {
        log.info("Sending notification to {}: {}", notification.getAddress(), notification.getTitle());

        // ✅ 1. 先存储到数据库（无论在线与否）
        saveToDatabase(notification);

        // 2. 检查用户在线状态
        boolean isOnline = checkUserOnline(notification.getAddress());

        if (isOnline) {
            // 3.1 在线：直接 WebSocket 推送
            pushViaWebSocket(notification);
        } else {
            // 3.2 离线：存储到 Redis
            saveToRedis(notification);
        }
    }

    /**
     * 保存到数据库
     */
    private void saveToDatabase(NotificationDTO notification) {
        try {
            NotificationEntity entity = new NotificationEntity();
            BeanUtils.copyProperties(notification, entity);
            notificationMapper.insert(entity);
            log.debug("Notification saved to database for user {}", notification.getAddress());
        } catch (Exception e) {
            log.error("Failed to save notification to database: {}", e.getMessage());
        }
    }

    /**
     * 检查用户在线状态
     */
    private boolean checkUserOnline(String address) {
        try {
            String onlineKey = "user:online:" + address;
            String online = redisTemplate.opsForValue().get(onlineKey);
            return "true".equals(online);
        } catch (Exception e) {
            log.warn("Failed to check online status for {}: {}", address, e.getMessage());
            return false;
        }
    }

    /**
     * WebSocket 实时推送
     */
    private void pushViaWebSocket(NotificationDTO notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            CustomWebSocketHandler.sendToUser(notification.getAddress(), message);
            log.info("✅ Notification sent via WebSocket to {}", notification.getAddress());
        } catch (Exception e) {
            log.error("❌ Failed to send notification via WebSocket: {}", e.getMessage());
            // 推送失败则降级存储到 Redis
            saveToRedis(notification);
        }
    }



    /**
     * 存储离线消息到 Redis
     */
    private void saveToRedis(NotificationDTO notification) {
        try {
            String key = "notification:offline:" + notification.getAddress();
            String json = objectMapper.writeValueAsString(notification);

            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, 49);  // 最多保留 50 条

            // ✅ 设置 7 天过期时间
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

            log.debug("Notification saved to Redis for offline user {}", notification.getAddress());
        } catch (Exception e) {
            log.warn("Failed to save notification to Redis: {}", e.getMessage());
        }
    }

    private void pushViaStomp(NotificationDTO notification) {
        try {
            // 直接发送到 /topic/notifications，包含 address 字段让前端过滤
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + notification.getAddress(),
                    notification
            );
            log.info("✅ Notification sent to /topic/notifications/{}", notification.getAddress());
        } catch (Exception e) {
            log.error("❌ Failed to send notification via STOMP: {}", e.getMessage(), e);
        }
    }

    @Override
    public void sendTradeNotification(String address, String tradeId, String type) {
        NotificationDTO notification = new NotificationDTO();
        notification.setAddress(address);
        notification.setTitle("交易通知");
        notification.setContent("交易 " + tradeId + " 状态更新：" + type);
        notification.setType(1);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        sendNotification(notification);
    }

    @Override
    public List<NotificationDTO> getUserNotifications(String address, Boolean unreadOnly) {
        try {
            List<NotificationEntity> entities = notificationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationEntity>()
                            .eq(NotificationEntity::getAddress, address)
                            .eq(unreadOnly != null && unreadOnly, NotificationEntity::getIsRead, false)
                            .orderByDesc(NotificationEntity::getCreateTime)
            );

            return entities.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get user notifications: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    @Override
    public void markAsRead(Long id, String address) {
        try {
            int rows = notificationMapper.markAsRead(id, address);
            if (rows > 0) {
                log.info("Notification {} marked as read for user {}", id, address);
            }
        } catch (Exception e) {
            log.error("Failed to mark notification as read: {}", e.getMessage());
        }
    }

    @Override
    public void markAllAsRead(String address) {
        try {
            int rows = notificationMapper.markAllAsRead(address);
            log.info("{} notifications marked as read for user {}", rows, address);
        } catch (Exception e) {
            log.error("Failed to mark all as read: {}", e.getMessage());
        }
    }
    @Override
    public void deleteNotification(Long id, String address) {
        try {
            notificationMapper.deleteById(id);
            log.info("Notification {} deleted for user {}", id, address);
        } catch (Exception e) {
            log.error("Failed to delete notification: {}", e.getMessage());
        }
    }


    @Override
    public List<NotificationDTO> getOfflineMessages(String address) {
        try {
            String key = "notification:offline:" + address;
            List<String> messages = redisTemplate.opsForList().range(key, 0, -1);

            if (messages == null || messages.isEmpty()) {
                return new ArrayList<>();
            }

            return messages.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, NotificationDTO.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void clearOfflineMessages(String address) {
        String key = "notification:offline:" + address;
        redisTemplate.delete(key);
        log.info("Cleared offline messages for user {}", address);
    }

    /**
     * Entity 转 DTO
     */
    private NotificationDTO convertToDTO(NotificationEntity entity) {
        NotificationDTO dto = new NotificationDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

}
