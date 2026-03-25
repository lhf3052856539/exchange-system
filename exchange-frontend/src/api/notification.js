// src/api/notification.js
import request from '@/utils/request'

/**
 * 获取用户通知列表
 */
export function getNotifications(params) {
    return request({
        url: '/notification/notifications',
        method: 'get',
        params
    })
}

/**
 * 标记通知为已读
 */
export function markAsRead(id) {
    return request({
        url: `/notification/notifications/${id}/read`,
        method: 'post'
    })
}

/**
 * 标记全部通知为已读
 */
export function markAllAsRead() {
    return request({
        url: '/notification/notifications/read-all',
        method: 'post'
    })
}

/**
 * 删除通知
 */
export function deleteNotification(id) {
    return request({
        url: `/notification/notifications/${id}`,
        method: 'delete'
    })
}

