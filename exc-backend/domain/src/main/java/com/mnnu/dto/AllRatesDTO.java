package com.mnnu.dto;
/**
 * 所有汇率DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AllRatesDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal usdToCny;
    private BigDecimal usdToGbp;
    private BigDecimal cnyToUsd;
    private BigDecimal gbpToUsd;
    private LocalDateTime timestamp;
}
