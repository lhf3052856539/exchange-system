package com.mnnu.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 金额转换工具
 */
public class AmountUtil {

    /**
     * 将USD金额转换为UT
     * 1 UT = 100 USD
     */
    public static BigDecimal usdToUt(BigDecimal usdAmount) {
        return usdAmount.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    /**
     * 将UT转换为USD
     */
    public static BigDecimal utToUsd(BigDecimal utAmount) {
        return utAmount.multiply(new BigDecimal("100"));
    }

    /**
     * 将EXTH转换为UT
     * 1 EXTH = 0.01 UT (900 EXTH = 9 UT)
     */
    public static BigDecimal exthToUt(BigDecimal exthAmount) {
        return exthAmount.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }

    /**
     * 将UT转换为EXTH
     */
    public static BigDecimal utToExth(BigDecimal utAmount) {
        return utAmount.multiply(new BigDecimal("100"));
    }

    /**
     * 计算可交易UT (MAX(1, EXTH余额*10倍))
     */
    public static BigDecimal calculateTradeableUt(BigDecimal exthBalance) {
        BigDecimal exthBased = exthBalance.multiply(new BigDecimal("10"));
        return exthBased.max(new BigDecimal("1"));
    }

    /**
     * 计算手续费,单边万分之一，1UT=100USD
     */
    public static BigDecimal calculateFee(BigDecimal amount, int feeRate, int denominator) {
        return amount.multiply(new BigDecimal(feeRate)).multiply(new BigDecimal(100))
                .divide(new BigDecimal(denominator), 6, RoundingMode.HALF_UP);
    }
}
