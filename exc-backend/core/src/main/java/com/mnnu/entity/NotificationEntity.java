package com.mnnu.entity;
/**
 * 通知记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@Data
@TableName("t_notification")
public class NotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String address;               // 接收地址

    private String title;                  // 标题

    private String content;                // 内容

    private Integer type;                   // 类型 1-交易通知 2-系统通知 3-争议通知

    private Boolean isRead;                 // 是否已读

    private LocalDateTime readTime;         // 阅读时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
