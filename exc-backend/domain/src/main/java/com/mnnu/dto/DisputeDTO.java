package com.mnnu.dto;
/**
 * 争议DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class DisputeDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String chainTradeId;
    private String initiator;
    private String accused;
    private String reason;
    private String evidence;
    private Integer status;
    private String statusDesc;
    private String result;
    private String resolver;
    private LocalDateTime createTime;
    private LocalDateTime resolveTime;

    // 多签钱包仲裁相关字段
    private String proposalId;
    private String proposalTxHash;
    private Integer voteCount;
    private Integer rejectCount;
    private Long deadline;
    private BigDecimal compensationAmount;
}