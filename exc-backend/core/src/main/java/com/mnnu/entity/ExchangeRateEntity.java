package com.mnnu.entity;
/**
 * 汇率记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_exchange_rate")
public class ExchangeRateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pair;                 // 货币对 (USD/CNY, USD/GBP)

    private BigDecimal rate;              // 汇率


    private LocalDateTime updateTime;      // 更新时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
