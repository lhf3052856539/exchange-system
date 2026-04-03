package com.mnnu.entity;
/**
 * 争议记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_dispute_record")
public class DisputeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeId;              // 交易 ID

    private String initiator;             // 发起方

    private String accused;               // 被指控方

    private String reason;                // 争议原因

    private String evidence;              // 证据（交易哈希等）

    private Integer status;                // 争议状态

    private String result;                 // 处理结果

    private String resolver;               // 处理人

    private LocalDateTime resolveTime;     // 处理时间

    // 仲裁提案相关字段
    private String proposalId;            // 链上提案 ID
    private String proposalTxHash;        // 提案创建交易哈希
    private BigDecimal compensationAmount; // 赔偿金额（USDT）
    private Integer voteCount;            // 赞成票数
    private Boolean executed = false;     // 是否已执行
    private Boolean rejected = false;     // 是否已驳回
    private Long deadline;                // 投票截止时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
