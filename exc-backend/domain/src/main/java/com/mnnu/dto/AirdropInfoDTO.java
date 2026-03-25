package com.mnnu.dto;
/**
 * 空投信息DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AirdropInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal totalAirdrop;
    private BigDecimal claimedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal perAddress;
    private Boolean hasClaimed;
    private BigDecimal myClaimed;
}
