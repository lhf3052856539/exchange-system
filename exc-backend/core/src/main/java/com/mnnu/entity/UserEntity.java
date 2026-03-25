package com.mnnu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("t_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String address;           // 钱包地址

    private Integer userType;          // 用户类型 0-新用户 1-普通用户 2-种子用户

    private Integer newUserTradeCount; // 新用户还需率先转账次数

    private BigDecimal exthBalance;    // EXTH余额

    private Boolean isBlacklisted;     // 是否被拉黑


    private LocalDateTime registerTime; // 注册时间

    private LocalDateTime lastActiveTime; // 最后活跃时间

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
