package com.mnnu.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_airdrop_whitelist")
public class AirdropWhitelistEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long configId;          // 关联的活动 ID
    private String address;         // 用户地址
    private BigDecimal amount;      // 可领数量
    private Boolean hasClaimed;     // 是否已领
    private String claimTxHash;     // 领取哈希
    private LocalDateTime claimTime;
}