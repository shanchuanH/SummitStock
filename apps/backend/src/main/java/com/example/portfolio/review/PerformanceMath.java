package com.example.portfolio.review;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public final class PerformanceMath {
    private static final MathContext MATH = MathContext.DECIMAL128;

    private PerformanceMath() {}

    public static BigDecimal returnBetween(List<DatedValue> values) {
        if (values == null || values.size() < 2) return null;
        var first = values.getFirst().value();
        var last = values.getLast().value();
        if (first == null || last == null || first.signum() <= 0) return null;
        return last.divide(first, MATH).subtract(BigDecimal.ONE);
    }

    public static BigDecimal maxDrawdown(List<DatedValue> values) {
        if (values == null || values.size() < 2) return null;
        BigDecimal peak = null;
        BigDecimal maximum = BigDecimal.ZERO;
        for (var point : values) {
            if (point.value() == null || point.value().signum() <= 0) continue;
            peak = peak == null ? point.value() : peak.max(point.value());
            maximum = maximum.max(peak.subtract(point.value()).divide(peak, MATH));
        }
        return peak == null ? null : maximum;
    }

    public static BigDecimal contribution(PositionPoint start, PositionPoint end, BigDecimal startEquity) {
        if (start == null
                || end == null
                || startEquity == null
                || startEquity.signum() <= 0
                || start.quantity() == null
                || start.quantity().signum() <= 0
                || end.quantity() == null
                || end.quantity().signum() <= 0) return null;
        var startPrice = start.marketValue().divide(start.quantity(), MATH);
        var endPrice = end.marketValue().divide(end.quantity(), MATH);
        if (startPrice.signum() <= 0) return null;
        return start.marketValue()
                .divide(startEquity, MATH)
                .multiply(endPrice.divide(startPrice, MATH).subtract(BigDecimal.ONE));
    }

    public record DatedValue(java.time.LocalDate date, BigDecimal value) {}

    public record PositionPoint(java.time.LocalDate date, BigDecimal quantity, BigDecimal marketValue) {}
}
