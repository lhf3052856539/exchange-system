package com.mnnu.entity;
/**
 * 空投记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@TableName("t_airdrop_record")
public class AirdropRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String address;               // 领取地址

    private BigDecimal amount;             // 领取数量

    private String txHash;                 // 交易哈希

    private Boolean isClaimed;             // 是否已领取

    private BigInteger blockNumber;        // 区块号（链上事件记录）

    private LocalDateTime claimTime;       // 领取时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
