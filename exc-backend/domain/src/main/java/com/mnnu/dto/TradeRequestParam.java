package com.mnnu.dto;
/**
 * 交易请求参数
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeRequestParam {
    private Long amount;                 // UT
    private String fromCurrency;         // 转出币种 RNB/GBP
    private String toCurrency;           // 转入币种
}
