package com.mnnu.service;
/**
 * 通知服务接口
 */


import com.mnnu.dto.NotificationDTO;

import java.util.List;

public interface NotificationService {

    /**
     * 发送通知
     */
    void sendNotification(NotificationDTO notification);

    /**
     * 发送交易通知
     */
    void sendTradeNotification(String address, String tradeId, String type);

    /**
     * 获取用户通知
     */
    List<NotificationDTO> getUserNotifications(String address, Boolean unreadOnly);

    /**
     * 标记为已读
     */
    void markAsRead(Long id, String address);

    /**
     * 标记全部已读
     */
    void markAllAsRead(String address);

    /**
     * 删除通知
     */
    void deleteNotification(Long id, String address);

    /**
     * 获取离线消息
     */
    List<NotificationDTO> getOfflineMessages(String address);

    /**
     * 清除离线消息
     */
    void clearOfflineMessages(String address);

}

