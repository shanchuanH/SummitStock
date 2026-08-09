package com.example.portfolio.estimates;

import java.math.BigDecimal;
import java.math.MathContext;

public final class EstimateDispersion {
    private EstimateDispersion() {}

    public static BigDecimal calculate(BigDecimal mean, BigDecimal high, BigDecimal low) {
        if (mean == null || high == null || low == null || mean.signum() == 0 || high.compareTo(low) < 0) return null;
        return high.subtract(low).divide(mean.abs(), MathContext.DECIMAL128);
    }
}
