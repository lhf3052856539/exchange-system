package com.mnnu.dto;
/**
 * 交易请求响应 DTO
 */
import lombok.Data;

import java.io.Serializable;

@Data
public class TradeRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Boolean success;
    private String message;
    private Integer queuePosition;
}
