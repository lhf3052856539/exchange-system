package com.mnnu.service;
/**
 * 汇率服务接口
 */


import com.mnnu.dto.AllRatesDTO;
import com.mnnu.dto.ExchangeRateDTO;


import java.math.BigDecimal;

public interface RateService {

    /**
     * 获取实时汇率
     */
    ExchangeRateDTO getCurrentRate(String fromCurrency, String toCurrency);

    /**
     * 获取所有汇率
     */
    AllRatesDTO getAllRates();

    /**
     * 更新汇率（从预言机）
     */
    void updateRates();

    /**
     * 计算兑换金额
     */
    BigDecimal calculateExchangeAmount(BigDecimal amount, String fromCurrency, String toCurrency);

    /**
     * 获取缓存汇率
     */
    ExchangeRateDTO getCachedRate(String pair);
}
