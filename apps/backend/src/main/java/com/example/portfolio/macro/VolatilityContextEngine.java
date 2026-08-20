package com.example.portfolio.macro;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public final class VolatilityContextEngine {
    public Result evaluate(Input input) {
        var vixTermRatio = ratio(input.vix(), input.vix3m());
        var vxnVixRatio = ratio(input.vxn(), input.vix());
        return new Result(
                input.vix(),
                MacroFactorEngine.percentile(input.vix(), input.vixHistory()),
                delta(input.vixHistory(), 1),
                delta(input.vixHistory(), 2),
                delta(input.vixHistory(), 5),
                input.vix3m(),
                vixTermRatio,
                termState(vixTermRatio, input.vixTermFlatLower(), input.vixTermBackwardation()),
                input.vxn(),
                MacroFactorEngine.percentile(input.vxn(), input.vxnHistory()),
                delta(input.vxnHistory(), 1),
                delta(input.vxnHistory(), 2),
                delta(input.vxnHistory(), 5),
                vxnVixRatio,
                input.vxn() == null || input.vix() == null ? null : input.vxn().subtract(input.vix()),
                techState(vxnVixRatio, input.techPremiumElevatedRatio()));
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0
                ? null
                : numerator.divide(denominator, MathContext.DECIMAL128);
    }

    private static BigDecimal delta(List<BigDecimal> history, int sessions) {
        var clean = history.stream().filter(java.util.Objects::nonNull).toList();
        return clean.size() <= sessions ? null : clean.getLast().subtract(clean.get(clean.size() - 1 - sessions));
    }

    private static TermState termState(BigDecimal ratio, BigDecimal flatLower, BigDecimal backwardation) {
        if (ratio == null) return TermState.MISSING;
        if (ratio.compareTo(flatLower) < 0) return TermState.CONTANGO;
        if (ratio.compareTo(backwardation) < 0) return TermState.FLAT;
        return TermState.BACKWARDATION;
    }

    private static TechStressState techState(BigDecimal ratio, BigDecimal elevated) {
        if (ratio == null) return TechStressState.MISSING;
        return ratio.compareTo(elevated) >= 0 ? TechStressState.ELEVATED : TechStressState.NORMAL;
    }

    public enum TermState {
        CONTANGO,
        FLAT,
        BACKWARDATION,
        MISSING
    }

    public enum TechStressState {
        NORMAL,
        ELEVATED,
        MISSING
    }

    public record Input(
            BigDecimal vix,
            List<BigDecimal> vixHistory,
            BigDecimal vix3m,
            BigDecimal vxn,
            List<BigDecimal> vxnHistory,
            BigDecimal vixTermFlatLower,
            BigDecimal vixTermBackwardation,
            BigDecimal techPremiumElevatedRatio) {}

    public record Result(
            BigDecimal vix,
            BigDecimal vixPercentile,
            BigDecimal vixDelta1d,
            BigDecimal vixDelta2d,
            BigDecimal vixDelta5d,
            BigDecimal vix3m,
            BigDecimal vixTermRatio,
            TermState vixTermState,
            BigDecimal vxn,
            BigDecimal vxnPercentile,
            BigDecimal vxnDelta1d,
            BigDecimal vxnDelta2d,
            BigDecimal vxnDelta5d,
            BigDecimal vxnVixRatio,
            BigDecimal vxnVixSpread,
            TechStressState techStressState) {}
}
