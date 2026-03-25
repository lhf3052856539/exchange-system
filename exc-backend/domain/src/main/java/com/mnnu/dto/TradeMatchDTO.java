package com.mnnu.dto;
/**
 * 交易匹配DTO
 */
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TradeMatchDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String partyA;
    private String partyB;
    private Long amount;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal exchangeRate;
    private String firstParty;           // 率先转账方
}
