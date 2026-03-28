package com.mnnu.dto;
/**
 * 空投信息 DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AirdropInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 核心字段
    private BigDecimal totalAirdrop;
    private BigDecimal claimedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal perAddress;
    private Boolean hasClaimed;
    private BigDecimal myClaimed;

    // 新增前端需要的字段（作为真正的字段，而不是方法）
    private BigDecimal totalAirdropAmount;
    private BigDecimal fixedAmount;
    private Boolean isActive;
}
