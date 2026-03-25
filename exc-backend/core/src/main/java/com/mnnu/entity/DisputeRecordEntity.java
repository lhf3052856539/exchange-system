package com.mnnu.entity;
/**
 * 争议记录实体
 */

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@Data
@TableName("t_dispute_record")
public class DisputeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeId;              // 交易ID

    private String initiator;             // 发起方

    private String accused;               // 被指控方

    private String reason;                // 争议原因

    private String evidence;              // 证据（交易哈希等）

    private Integer status;                // 争议状态

    private String result;                 // 处理结果

    private String resolver;               // 处理人

    private LocalDateTime resolveTime;     // 处理时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
