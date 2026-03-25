package com.mnnu.dto;
/**
 * 队列项DTO
 */
import lombok.Data;

import java.io.Serializable;

@Data
public class QueueItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String address;
    private Long amount;
    private String fromCurrency;
    private String toCurrency;
    private Long waitTime;               // 等待时间（秒）
}
