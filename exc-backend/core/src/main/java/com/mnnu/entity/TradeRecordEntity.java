package com.mnnu.entity;
/**
 * 交易记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@TableName("t_trade_record")
public class TradeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeId;            // 交易ID（链上交易ID）
    /**
     * 链上交易索引 ID
     */
    private Long chainTradeId;

    private String partyA;             // 率先转账方

    private String partyB;             // 履约方

    private Integer partyAType;         // A方用户类型

    private Integer partyBType;         // B方用户类型

    private BigDecimal amount;          // 交易额度（UT）

    private BigDecimal amountA;          // A方需转账金额（根据汇率计算）

    private BigDecimal amountB;          // B方需转账金额

    private String fromCurrency;        // A方转出币种（RNB/GBP）

    private String toCurrency;          // A方转入币种

    private BigDecimal exchangeRate;     // 汇率

    private BigDecimal exthReward;       // EXTH奖励

    private BigDecimal feeAmount;       //手续费金额

    private Integer status;              // 交易状态

    private Boolean isPartyAConfirmed;   // A方是否已确认转账

    private Boolean isPartyBConfirmed;   // B方是否已确认转账

    private LocalDateTime matchTime;     // 匹配时间

    private LocalDateTime partyAConfirmTime; // A方确认时间

    private LocalDateTime partyBConfirmTime; // B方确认时间

    private LocalDateTime completeTime;   // 完成时间

    private LocalDateTime expireTime;     // 过期时间

    private Boolean isDisputed;           // 是否有争议

    private String disputedParty;         // 被争议方

    private Integer disputeStatus;        // 争议状态

    private String txHashA;               // A方转账交易哈希

    private String txHashB;               // B 方转账交易哈希

    private String chainTxHash;           // 链上交易完成时的交易哈希


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
