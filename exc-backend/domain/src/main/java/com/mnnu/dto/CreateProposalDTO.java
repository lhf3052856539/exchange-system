package com.mnnu.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建提案请求 DTO
 */
@Data
public class CreateProposalDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String description;
    private String targetContract;
    private BigDecimal value;
    private String callData;
}
