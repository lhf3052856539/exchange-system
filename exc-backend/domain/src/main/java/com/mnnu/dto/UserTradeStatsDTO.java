package com.mnnu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 用户交易统计DTO
 */
@Data
public class UserTradeStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String address;
    private Integer totalTrades;
    private Integer completedTrades;
    private Integer disputedTrades;
    private BigDecimal totalVolume;
    private BigDecimal totalReward;
    private Integer successRate;         // 成功率百分比
}
