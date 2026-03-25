package com.mnnu.dto;
/**
 * 通知DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String address;
    private String title;
    private String content;
    private Integer type;
    private Boolean isRead;
    private LocalDateTime createTime;
}
