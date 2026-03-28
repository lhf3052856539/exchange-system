package com.mnnu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
@Data
@TableName("t_dao_proposal_record")
public class ProposalRecordEntity {
    private Long id;
    private String proposalId;        // 链上ID
    private String txHash;
    private String description;
    private String proposer;
    private String targetContract;
    private BigDecimal value = BigDecimal.ZERO;
    private String callData;
    private Integer status;
    private Long blockNumber;
    private Long eta;
    private Long snapshotBlock;
    private BigDecimal yesVotes = BigDecimal.ZERO;
    private BigDecimal noVotes = BigDecimal.ZERO;
    private Long deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
