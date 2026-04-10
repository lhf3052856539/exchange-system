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

        // 1. 存数据库（唯一数据源）
        saveToDatabase(notification);

        // 2. 尝试 WebSocket 推送
        pushViaWebSocket(notification);
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
        }
    }

    @Override
    public void sendUserUpgradeNotification(String address, int newType) {
        String title = "身份变更通知";
        String content = "";

        if (newType == 0) {
            content = "欢迎加入！您有 3 次率先转账任务，完成后即可升级。";
        } else if (newType == 1) {
            content = "恭喜！您已升级为普通用户，解锁全部交易功能。";
        } else if (newType == 2) {
            content = "尊贵身份！您的 EXTH 余额已达标，正式升级为种子用户。";
        }

        // 复用内部已有的发送逻辑（WebSocket + DB）
        NotificationDTO notification = new NotificationDTO();
        notification.setAddress(address);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(2); // 系统通知类型
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());

        this.sendNotification(notification);
    }


    @Override
    public void sendTradeNotification(String address, String tradeId, String type) {
        NotificationDTO notification = new NotificationDTO();
        notification.setAddress(address);
        notification.setTitle("交易通知");

        String content = switch (type) {
            case "matched" -> "您的交易 " + tradeId + " 已匹配成功，请尽快确认";
            case "created" -> "您的交易 " + tradeId + " 已成功创建并上链，等待对方确认";
            case "party_a_confirmed" -> "甲方已确认交易 " + tradeId + "，请您进行确认";
            case "party_b_confirmed" -> "乙方已确认交易 " + tradeId + "，请您进行最终确认";
            case "pending_chain_confirm" -> "交易 " + tradeId + " 正在等待链上确认";
            case "completed" -> "交易 " + tradeId + " 已完成";
            case "disputed" -> "交易 " + tradeId + " 已被提起争议";
            case "expired" -> "交易 " + tradeId + " 已超时取消";
            default -> "交易 " + tradeId + " 状态更新：" + type;
        };

        notification.setContent(content);
        notification.setType(1);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        sendNotification(notification);
    }

    @Override
    public void sendDaoProposalNotification(String address, String proposalId, String title, String action) {
        NotificationDTO notification = new NotificationDTO();
        notification.setAddress(address);
        notification.setTitle("🗳️ DAO 提案通知");

        String content = switch (action) {
            case "created" -> "新提案已创建：" + title;
            case "voted" -> "您已成功投票，提案：" + title;
            case "queued" -> "提案 " + title + " 已进入公示期";
            case "executed" -> "提案 " + title + " 已执行成功";
            case "cancelled" -> "提案 " + title + " 已取消";
            case "defeated" -> "提案 " + title + " 未通过";
            default -> "提案 " + title + " 状态更新：" + action;
        };

        notification.setContent(content);
        notification.setType(4);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        sendNotification(notification);
    }

    @Override
    public void sendArbitrationNotification(String address, String tradeId, String role, String action) {
        NotificationDTO notification = new NotificationDTO();
        notification.setAddress(address);
        notification.setTitle("⚖️ 仲裁通知");

        String content = switch (action) {
            case "initiated" -> "交易 " + tradeId + " 已被提起仲裁";
            case "proposal_created" -> "交易 " + tradeId + " 的仲裁提案已创建";
            case "voted" -> "您已成功对交易 " + tradeId + " 的仲裁进行投票";
            case "executed" -> "交易 " + tradeId + " 的仲裁已执行";
            default -> "交易 " + tradeId + " 仲裁状态更新：" + action;
        };

        notification.setContent(content);
        notification.setType(2);
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        sendNotification(notification);
    }

    @Override
    public void sendSystemNotification(String address, String title, String content) {
        NotificationEntity notification = new NotificationEntity();
        notification.setAddress(address);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(2); // 2-系统通知
        notification.setIsRead(false);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);

        log.info("System notification sent to {}: {}", address, title);
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

    /**
     * Entity 转 DTO
     */
    private NotificationDTO convertToDTO(NotificationEntity entity) {
        NotificationDTO dto = new NotificationDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

}
