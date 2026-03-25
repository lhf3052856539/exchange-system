package com.mnnu.controller;

import com.mnnu.apis.NotificationApi;
import com.mnnu.config.CurrentUser;
import com.mnnu.dto.NotificationDTO;
import com.mnnu.service.NotificationService;
import com.mnnu.vo.JsonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/apis/notification")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    @Override
    public JsonVO<List<NotificationDTO>> getNotifications(
            @CurrentUser String address,
            @RequestParam(required = false) Boolean unreadOnly
    ) {
        try {
            List<NotificationDTO> notifications = notificationService.getUserNotifications(address, unreadOnly);
            return JsonVO.success(notifications);
        } catch (Exception e) {
            log.error("Get notifications failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<Void> markAsRead(
            @CurrentUser String address,
            @PathVariable Long id
    ) {
        try {
            notificationService.markAsRead(id, address);
            return JsonVO.success(null);
        } catch (Exception e) {
            log.error("Mark as read failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<Void> markAllAsRead(@CurrentUser String address) {
        try {
            notificationService.markAllAsRead(address);
            return JsonVO.success(null);
        } catch (Exception e) {
            log.error("Mark all as read failed", e);
            return JsonVO.error(e.getMessage());
        }
    }

    @Override
    public JsonVO<Void> deleteNotification(
            @CurrentUser String address,
            @PathVariable Long id
    ) {
        try {
            notificationService.deleteNotification(id, address);
            return JsonVO.success(null);
        } catch (Exception e) {
            log.error("Delete notification failed", e);
            return JsonVO.error(e.getMessage());
        }
    }
}
