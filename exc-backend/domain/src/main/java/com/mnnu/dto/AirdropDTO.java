package com.mnnu.dto;
/**
 * 空投DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AirdropDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String address;
    private BigDecimal amount;
    private String txHash;
    private LocalDateTime claimTime;
}

