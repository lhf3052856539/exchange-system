package com.mnnu.dto;
/**
 * 用户DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String address;
    private Integer userType;
    private String userTypeDesc;
    private Integer newUserTradeCount;
    private BigDecimal exthBalance;
    private Long tradeableUt;
    private Boolean isBlacklisted;
    private LocalDateTime registerTime;
    private LocalDateTime lastActiveTime;
    private String token;
}

