package com.mnnu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * 提案 DTO
 */
@Data
public class ProposalDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // DAO 提案相关字段
    private BigInteger proposalId;
    private String description;
    private BigInteger deadline;
    private BigInteger yesVotes;
    private BigInteger noVotes;
    private String callData;
    private String targetContract;
    private BigInteger value;
    private BigInteger eta;
    private BigInteger snapshotBlock;
    private Integer state;
    private String stateDesc;
    private Boolean hasVoted;
    private String proposer;
    private BigInteger startTime;

}