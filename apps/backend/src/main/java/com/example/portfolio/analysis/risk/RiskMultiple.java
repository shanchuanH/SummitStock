package com.example.portfolio.analysis.risk;

import java.math.BigDecimal;
import java.math.MathContext;

public final class RiskMultiple {
    private static final MathContext MATH = MathContext.DECIMAL128;

    private RiskMultiple() {}

    public static BigDecimal initialRiskPerShare(BigDecimal entryPrice, BigDecimal initialStop) {
        if (entryPrice == null || initialStop == null || entryPrice.compareTo(initialStop) <= 0) return null;
        return entryPrice.subtract(initialStop);
    }

    public static BigDecimal currentR(
            BigDecimal currentPrice, BigDecimal initialEntryPrice, BigDecimal initialRiskPerShare) {
        if (currentPrice == null
                || initialEntryPrice == null
                || initialRiskPerShare == null
                || initialRiskPerShare.signum() <= 0) return null;
        return currentPrice.subtract(initialEntryPrice).divide(initialRiskPerShare, MATH);
    }
}
