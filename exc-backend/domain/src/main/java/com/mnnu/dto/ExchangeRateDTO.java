package com.mnnu.dto;
/**
 * 汇率DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExchangeRateDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String pair;
    private BigDecimal rate;
    private LocalDateTime updateTime;
    private  LocalDateTime timestamp;
}
