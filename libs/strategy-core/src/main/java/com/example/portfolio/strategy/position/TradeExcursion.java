package com.example.portfolio.strategy.position;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TradeExcursion {
    private TradeExcursion() {}

    public static Metrics calculate(
            BigDecimal entry, BigDecimal initialStop, BigDecimal high, BigDecimal low, BigDecimal exit) {
        var risk = entry.subtract(initialStop);
        if (risk.signum() <= 0) throw new IllegalArgumentException("initial risk must be positive");
        return new Metrics(
                ratio(exit.subtract(entry), risk), ratio(high.subtract(entry), risk), ratio(low.subtract(entry), risk));
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal risk) {
        return value.divide(risk, 10, RoundingMode.HALF_EVEN).stripTrailingZeros();
    }

    public record Metrics(BigDecimal realizedR, BigDecimal mfeR, BigDecimal maeR) {}
}
