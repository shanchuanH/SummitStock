package com.example.portfolio.estimates;

import java.math.BigDecimal;
import java.math.MathContext;

public final class EstimateConsensusMath {
    private EstimateConsensusMath() {}

    public static BigDecimal priorConsensus(BigDecimal current, BigDecimal fractionalChange) {
        if (current == null || fractionalChange == null) return null;
        var denominator = BigDecimal.ONE.add(fractionalChange);
        return denominator.signum() <= 0 ? null : current.divide(denominator, MathContext.DECIMAL128);
    }
}
