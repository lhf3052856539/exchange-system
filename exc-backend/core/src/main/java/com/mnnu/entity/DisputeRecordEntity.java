package com.mnnu.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_dispute_record")
public class DisputeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String chainTradeId;

    private String initiator;

    private String accused;

    private String reason;

    private String evidence;

    @TableField("proposal_status")
    private Integer proposalStatus;

    private String proposalId;

    private String proposalTxHash;

    private BigDecimal compensationAmount;

    private Integer voteCount;

    private Integer rejectCount;

    private Long deadline;

    private String result;

    private LocalDateTime resolveTime;

    private LocalDateTime createTime;
}
