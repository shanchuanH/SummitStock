package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EstimateRevisionEngine {
    private static final BigDecimal HIGH_DISPERSION = new BigDecimal("0.50");
    private static final MathContext MATH = MathContext.DECIMAL128;

    public RevisionResult evaluate(List<Observation> observations, Instant now) {
        if (observations.isEmpty()) return RevisionResult.missing(now);
        var latestPeriod = observations.stream()
                .max(Comparator.comparing(Observation::periodEnd).thenComparing(Observation::dataAsOf))
                .orElseThrow();
        var relevant = observations.stream()
                .filter(value -> value.periodEnd().equals(latestPeriod.periodEnd()))
                .filter(value -> value.horizon().equals(latestPeriod.horizon()))
                .toList();
        var eps = changes(relevant, EarningsEstimateResult.EstimateType.EPS, now);
        var revenue = changes(relevant, EarningsEstimateResult.EstimateType.REVENUE, now);
        var state7 = classify(eps.day7(), revenue.day7());
        var state30 = classify(eps.day30(), revenue.day30());
        var state90 = classify(eps.day90(), revenue.day90());
        var overall = overall(state7, state30, state90);
        var latest = relevant.stream()
                .max(Comparator.comparing(Observation::dataAsOf))
                .orElseThrow();
        var dispersion = EstimateDispersion.calculate(latest.mean(), latest.high(), latest.low());
        var quality = quality(eps, revenue, latest.analystCount(), dispersion);
        return new RevisionResult(
                latest.periodEnd(),
                latest.horizon(),
                state7,
                state30,
                state90,
                overall,
                eps.day7(),
                eps.day30(),
                eps.day90(),
                revenue.day7(),
                revenue.day30(),
                revenue.day90(),
                latest.analystCount(),
                dispersion,
                quality,
                now);
    }

    private static Changes changes(List<Observation> values, EarningsEstimateResult.EstimateType type, Instant now) {
        var typed = values.stream()
                .filter(value -> value.estimateType() == type)
                .sorted(Comparator.comparing(Observation::dataAsOf))
                .toList();
        if (typed.isEmpty()) return new Changes(null, null, null);
        var latest = typed.getLast();
        return new Changes(
                change(latest, typed, now.minusSeconds(7 * 86400L)),
                change(latest, typed, now.minusSeconds(30 * 86400L)),
                change(latest, typed, now.minusSeconds(90 * 86400L)));
    }

    private static BigDecimal change(Observation latest, List<Observation> history, Instant cutoff) {
        var baseline = history.stream()
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .max(Comparator.comparing(Observation::dataAsOf));
        if (baseline.isEmpty() || baseline.orElseThrow().mean().signum() == 0) return null;
        return latest.mean().divide(baseline.orElseThrow().mean(), MATH).subtract(BigDecimal.ONE);
    }

    private static RevisionState classify(BigDecimal eps, BigDecimal revenue) {
        var values = new ArrayList<BigDecimal>();
        if (eps != null) values.add(eps);
        if (revenue != null) values.add(revenue);
        if (values.isEmpty()) return RevisionState.MISSING;
        var average = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MATH);
        if (average.compareTo(new BigDecimal("0.05")) >= 0) return RevisionState.STRONGLY_POSITIVE;
        if (average.compareTo(new BigDecimal("0.01")) >= 0) return RevisionState.POSITIVE;
        if (average.compareTo(new BigDecimal("-0.01")) > 0) return RevisionState.FLAT;
        if (average.compareTo(new BigDecimal("-0.05")) > 0) return RevisionState.NEGATIVE;
        return RevisionState.STRONGLY_NEGATIVE;
    }

    private static RevisionState overall(RevisionState day7, RevisionState day30, RevisionState day90) {
        if (day30 != RevisionState.MISSING) return day30;
        if (day7 != RevisionState.MISSING) return day7;
        return day90;
    }

    private static ProviderModels.QualityStatus quality(
            Changes eps, Changes revenue, Integer analysts, BigDecimal dispersion) {
        if (eps.allMissing() && revenue.allMissing()) return ProviderModels.QualityStatus.MISSING;
        if (eps.allMissing()
                || revenue.allMissing()
                || analysts == null
                || analysts < 3
                || dispersion == null
                || isHighDispersion(dispersion)) {
            return ProviderModels.QualityStatus.PARTIAL;
        }
        return ProviderModels.QualityStatus.HEALTHY;
    }

    public static boolean isHighDispersion(BigDecimal dispersion) {
        return dispersion != null && dispersion.compareTo(HIGH_DISPERSION) > 0;
    }

    public enum RevisionState {
        STRONGLY_POSITIVE,
        POSITIVE,
        FLAT,
        NEGATIVE,
        STRONGLY_NEGATIVE,
        MISSING
    }

    public record Observation(
            EarningsEstimateResult.EstimateType estimateType,
            EarningsEstimateResult.PeriodType periodType,
            LocalDate periodEnd,
            String horizon,
            BigDecimal mean,
            BigDecimal high,
            BigDecimal low,
            Integer analystCount,
            Instant dataAsOf) {}

    public record RevisionResult(
            LocalDate periodEnd,
            String horizon,
            RevisionState day7,
            RevisionState day30,
            RevisionState day90,
            RevisionState overall,
            BigDecimal epsDay7,
            BigDecimal epsDay30,
            BigDecimal epsDay90,
            BigDecimal revenueDay7,
            BigDecimal revenueDay30,
            BigDecimal revenueDay90,
            Integer analystCount,
            BigDecimal dispersion,
            ProviderModels.QualityStatus quality,
            Instant dataAsOf) {
        static RevisionResult missing(Instant now) {
            return new RevisionResult(
                    null,
                    "UNKNOWN",
                    RevisionState.MISSING,
                    RevisionState.MISSING,
                    RevisionState.MISSING,
                    RevisionState.MISSING,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ProviderModels.QualityStatus.MISSING,
                    now);
        }
    }

    private record Changes(BigDecimal day7, BigDecimal day30, BigDecimal day90) {
        boolean allMissing() {
            return day7 == null && day30 == null && day90 == null;
        }
    }
}
