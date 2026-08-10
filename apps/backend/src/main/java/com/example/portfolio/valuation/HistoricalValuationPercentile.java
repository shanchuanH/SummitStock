package com.example.portfolio.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public final class HistoricalValuationPercentile {
    private HistoricalValuationPercentile() {}

    public static BigDecimal lowerIsCheaper(BigDecimal current, List<BigDecimal> history) {
        if (current == null || history.isEmpty()) return null;
        long atOrBelow = history.stream()
                .filter(value -> value != null && value.compareTo(current) <= 0)
                .count();
        long valid = history.stream().filter(java.util.Objects::nonNull).count();
        return valid == 0
                ? null
                : BigDecimal.valueOf(atOrBelow).divide(BigDecimal.valueOf(valid), MathContext.DECIMAL128);
    }

    public static BigDecimal higherIsCheaper(BigDecimal current, List<BigDecimal> history) {
        var percentile = lowerIsCheaper(current, history);
        return percentile == null ? null : BigDecimal.ONE.subtract(percentile);
    }
}
