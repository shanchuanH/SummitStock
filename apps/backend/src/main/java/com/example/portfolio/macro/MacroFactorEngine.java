package com.example.portfolio.macro;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public final class MacroFactorEngine {
    public Result evaluate(Input input) {
        var volatility = percentile(input.vix(), input.vixHistory());
        var credit = percentile(input.hySpread(), input.hySpreadHistory());
        var rate = rateStress(input.tenYearYield(), input.tenYearYield90dAgo(), input.fedFunds());
        var curve = curve(input.tenYearYield(), input.twoYearYield());
        if (volatility == null && credit == null && rate == null) {
            return new Result(null, null, null, curve, null, Quality.MISSING);
        }
        var stress = weighted(volatility, credit, input.realizedVolatilityStress());
        return new Result(
                volatility,
                credit,
                rate,
                curve,
                stress == null ? null : BigDecimal.ONE.subtract(stress),
                volatility != null && credit != null ? Quality.HEALTHY : Quality.PARTIAL);
    }

    static BigDecimal percentile(BigDecimal current, List<BigDecimal> history) {
        if (current == null) return null;
        var clean = history.stream().filter(java.util.Objects::nonNull).toList();
        if (clean.isEmpty()) return null;
        long atOrBelow =
                clean.stream().filter(value -> value.compareTo(current) <= 0).count();
        return BigDecimal.valueOf(atOrBelow).divide(BigDecimal.valueOf(clean.size()), MathContext.DECIMAL128);
    }

    private static BigDecimal rateStress(BigDecimal current, BigDecimal prior, BigDecimal fedFunds) {
        if (current == null || prior == null) return null;
        var rise = current.subtract(prior).max(BigDecimal.ZERO).divide(new BigDecimal("2"), MathContext.DECIMAL128);
        var level = fedFunds == null ? BigDecimal.ZERO : fedFunds.divide(new BigDecimal("10"), MathContext.DECIMAL128);
        return rise.add(level).min(BigDecimal.ONE);
    }

    private static CurveState curve(BigDecimal tenYear, BigDecimal twoYear) {
        if (tenYear == null || twoYear == null) return CurveState.MISSING;
        var spread = tenYear.subtract(twoYear);
        if (spread.signum() < 0) return CurveState.INVERTED;
        if (spread.compareTo(new BigDecimal("0.25")) < 0) return CurveState.FLAT;
        return CurveState.NORMAL;
    }

    private static BigDecimal weighted(BigDecimal volatility, BigDecimal credit, BigDecimal realized) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        if (volatility != null) {
            total = total.add(volatility.multiply(new BigDecimal("0.4")));
            weight = weight.add(new BigDecimal("0.4"));
        }
        if (credit != null) {
            total = total.add(credit.multiply(new BigDecimal("0.4")));
            weight = weight.add(new BigDecimal("0.4"));
        }
        if (realized != null) {
            total = total.add(realized.multiply(new BigDecimal("0.2")));
            weight = weight.add(new BigDecimal("0.2"));
        }
        return weight.signum() == 0
                ? null
                : total.divide(weight, MathContext.DECIMAL128).min(BigDecimal.ONE);
    }

    public enum CurveState {
        NORMAL,
        FLAT,
        INVERTED,
        MISSING
    }

    public enum Quality {
        HEALTHY,
        PARTIAL,
        MISSING
    }

    public record Input(
            BigDecimal vix,
            List<BigDecimal> vixHistory,
            BigDecimal hySpread,
            List<BigDecimal> hySpreadHistory,
            BigDecimal tenYearYield,
            BigDecimal tenYearYield90dAgo,
            BigDecimal twoYearYield,
            BigDecimal fedFunds,
            BigDecimal realizedVolatilityStress) {}

    public record Result(
            BigDecimal volatilityStress,
            BigDecimal creditStress,
            BigDecimal rateStress,
            CurveState curveState,
            BigDecimal stressResilience,
            Quality quality) {}
}
