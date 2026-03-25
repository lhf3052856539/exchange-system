package com.mnnu.apis;

import com.mnnu.config.CurrentUser;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.vo.JsonVO;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知中心 API 接口
 */
public interface NotificationApi {

    /**
     * 获取用户通知列表
     */
    @GetMapping("/notifications")
    JsonVO<List<NotificationDTO>> getNotifications(
            @CurrentUser String address,
            @RequestParam(required = false) Boolean unreadOnly
    );

    /**
     * 标记通知为已读
     */
    @PostMapping("/notifications/{id}/read")
    JsonVO<Void> markAsRead(
            @CurrentUser String address,
            @PathVariable Long id
    );

    /**
     * 标记全部通知为已读
     */
    @PostMapping("/notifications/read-all")
    JsonVO<Void> markAllAsRead(@CurrentUser String address);

    /**
     * 删除通知
     */
    @DeleteMapping("/notifications/{id}")
    JsonVO<Void> deleteNotification(
            @CurrentUser String address,
            @PathVariable Long id
    );

}
