package com.mnnu.utils;

import org.web3j.utils.Convert;
import java.math.BigDecimal;
import java.math.BigInteger;

public class Web3jUtil {

    public static final int TOKEN_DECIMALS = 6;
    private static final BigDecimal DECIMAL_FACTOR = BigDecimal.TEN.pow(TOKEN_DECIMALS);

    /**
     * 将人类可读金额转换为链上值（乘以精度）
     * @param humanAmount 人类可读金额（如：100.5）
     * @return 链上金额（如：100500000）
     */
    public static BigInteger toChainUnit(BigDecimal humanAmount) {
        return humanAmount.multiply(DECIMAL_FACTOR).toBigInteger();
    }

    /**
     * 将链上值转换为人类可读金额（除以精度）
     * @param chainAmount 链上金额（BigInteger）
     * @return 人类可读金额
     */
    public static BigDecimal fromChainUnit(BigInteger chainAmount) {
        if (chainAmount == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(chainAmount).divide(DECIMAL_FACTOR, TOKEN_DECIMALS, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 将链上值转换为人类可读金额（BigDecimal 版本）
     */
    public static BigDecimal fromChainUnit(BigDecimal chainAmount) {
        if (chainAmount == null) {
            return BigDecimal.ZERO;
        }
        return chainAmount.divide(DECIMAL_FACTOR, TOKEN_DECIMALS, BigDecimal.ROUND_HALF_UP);
    }

}
