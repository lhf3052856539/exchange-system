package com.mnnu.entity;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_airdrop_config")
public class AirdropConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String proposalId;      // 关联的 DAO 提案 ID
    private String merkleRoot;      // Merkle 根哈希
    private BigDecimal totalAmount; // 总预算
    private String tokenAddress;    // 代币地址
    private Integer status;         // 0-待开始, 1-进行中, 2-已结束
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
