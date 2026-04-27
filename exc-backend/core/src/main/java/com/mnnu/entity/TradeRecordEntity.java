package com.mnnu.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_trade_record")
public class TradeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeId;

    private Long chainTradeId;

    private String partyA;

    private String partyB;

    private Integer partyAType;

    private Integer partyBType;

    private BigDecimal amount;

    private BigDecimal amountA;

    private BigDecimal amountB;

    private String fromCurrency;

    private String toCurrency;

    private BigDecimal exchangeRate;

    private BigDecimal exthReward;

    private BigDecimal feeAmount;

    private Integer status;

    private LocalDateTime matchTime;

    private LocalDateTime partyAConfirmTime;

    private LocalDateTime partyBConfirmTime;

    private LocalDateTime completeTime;

    private LocalDateTime expireTime;

    private String disputedParty;

    private Integer disputeStatus;

    private String txHashA;

    private String txHashB;

    private String chainTxHash;

    private LocalDateTime createTime;
}
