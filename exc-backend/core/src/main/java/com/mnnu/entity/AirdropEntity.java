package com.mnnu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 空投实体类
 */
@Data
@TableName("airdrop")
public class AirdropEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户地址
     */
    private String address;

    /**
     * 空投总金额
     */
    private BigDecimal totalAmount;

    /**
     * 每个地址可领取的金额
     */
    private BigDecimal perAddressAmount;

    /**
     * 领取金额（针对用户记录）
     */
    private BigDecimal amount;

    /**
     * 是否已领取
     */
    private Boolean hasClaimed;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 领取时间
     */
    private LocalDateTime claimTime;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
