package com.mnnu.dto;
/**
 * 交易DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TradeDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String tradeId;
    private String partyA;
    private String partyB;
    private Integer partyAType;
    private Integer partyBType;
    private BigDecimal amount;          // UT
    private BigDecimal amountA;          // A方需转账金额
    private BigDecimal amountB;          // B方需转账金额
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal exthReward;
    private Integer status;
    private String statusDesc;
    private Boolean isPartyAConfirmed;
    private Boolean isPartyBConfirmed;
    private LocalDateTime matchTime;
    private LocalDateTime partyAConfirmTime;
    private LocalDateTime partyBConfirmTime;
    private LocalDateTime expireTime;
    private Boolean isDisputed;
    private String disputedParty;
    private Integer disputeStatus;
    private String txHashA;              // A方转账交易哈希
    private String txHashB;              // B方转账交易哈希
    private Long chainTradeId;           // 链上交易索引 ID
    private String myRole;               // 当前用户在交易中的角色：PARTY_A/PARTY_B
}
