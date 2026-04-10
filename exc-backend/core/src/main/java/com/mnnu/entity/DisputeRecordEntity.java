package com.mnnu.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 争议记录实体 (对应 t_dispute_record)
 */
@Data
@TableName("t_dispute_record")
public class DisputeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 交易 ID
     */
    private String tradeId;

    /**
     * 发起方
     */
    private String initiator;

    /**
     * 被指控方
     */
    private String accused;

    /**
     * 争议原因
     */
    private String reason;

    /**
     * 证据（交易哈希等）
     */
    private String evidence;

    /**
     * 争议状态: 0-等待链上确认, 1-处理中, 2-已解决, 3-已驳回, 4-已过期
     */
    @TableField("proposal_status")
    private Integer proposalStatus;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 处理人
     */
    private String resolver;

    /**
     * 处理时间
     */
    private LocalDateTime resolveTime;

    // --- 仲裁提案相关字段 ---

    /**
     * 链上提案 ID
     */
    private String proposalId;

    /**
     * 提案创建交易哈希
     */
    private String proposalTxHash;

    /**
     * 赔偿金额（USDT）
     */
    private BigDecimal compensationAmount;

    /**
     * 赞成票数
     */
    private Integer voteCount;

    /**
     * 反对票数
     */
    private Integer rejectCount;

    /**
     * 投票截止时间戳 (对应数据库 BIGINT)
     */
    private Long deadline;

    /**
     * 记录创建时间
     */
    private LocalDateTime createTime;


}
