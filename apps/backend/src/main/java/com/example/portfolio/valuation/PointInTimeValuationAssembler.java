package com.example.portfolio.valuation;

import com.example.portfolio.financialaggregation.CanonicalFinancialAggregation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class PointInTimeValuationAssembler {
    public PointInTimeInputs assemble(
            LocalDate marketDate,
            Instant decisionCutoff,
            Instant priceCompletedSessionAvailability,
            List<MetricPoint> metrics,
            List<EstimatePoint> estimates) {
        var trailingEps = ttm("DILUTED_EPS", decisionCutoff, metrics);
        var forwardEps = forwardEps(marketDate, decisionCutoff, estimates);
        var revenue = ttm("REVENUE", decisionCutoff, metrics);
        var freeCashFlow = ttm("FREE_CASH_FLOW", decisionCutoff, metrics);
        var cash = latest("CASH", decisionCutoff, metrics);
        var totalDebt = latest("TOTAL_DEBT", decisionCutoff, metrics);
        var commonShares = latest("COMMON_SHARES_OUTSTANDING", decisionCutoff, metrics);
        var evidenceDataAsOf = java.util.stream.Stream.of(
                        trailingEps, forwardEps, revenue, freeCashFlow, cash, totalDebt, commonShares)
                .filter(java.util.Objects::nonNull)
                .map(SelectedValue::dataAsOf)
                .reduce(priceCompletedSessionAvailability, PointInTimeValuationAssembler::later);
        return new PointInTimeInputs(
                new Inputs(
                        value(trailingEps),
                        value(forwardEps),
                        value(revenue),
                        value(freeCashFlow),
                        value(cash),
                        value(totalDebt),
                        value(commonShares)),
                evidenceDataAsOf);
    }

    private static SelectedValue ttm(String metric, Instant cutoff, List<MetricPoint> values) {
        var aggregate = CanonicalFinancialAggregation.ttm(
                metric,
                cutoff,
                values.stream()
                        .map(value -> new CanonicalFinancialAggregation.Observation(
                                value.periodType(),
                                value.periodEnd(),
                                value.fiscalYear(),
                                value.fiscalQuarter(),
                                value.metricCode(),
                                value.value(),
                                value.periodQuality(),
                                value.metricQuality(),
                                value.dataAsOf()))
                        .toList());
        return aggregate == null ? null : new SelectedValue(aggregate.value(), aggregate.dataAsOf());
    }

    private static SelectedValue latest(String metric, Instant cutoff, List<MetricPoint> values) {
        return values.stream()
                .filter(value -> value.metricCode().equals(metric))
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .max(Comparator.comparing(MetricPoint::periodEnd).thenComparing(MetricPoint::dataAsOf))
                .map(value -> new SelectedValue(value.value(), value.dataAsOf()))
                .orElse(null);
    }

    private static SelectedValue forwardEps(LocalDate marketDate, Instant cutoff, List<EstimatePoint> values) {
        var earliestPeriod = values.stream()
                .filter(value -> value.periodEnd().compareTo(marketDate) >= 0)
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .map(EstimatePoint::periodEnd)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (earliestPeriod == null) return null;
        return values.stream()
                .filter(value -> value.periodEnd().equals(earliestPeriod))
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .max(Comparator.comparing(EstimatePoint::dataAsOf))
                .map(value -> new SelectedValue(value.meanValue(), value.dataAsOf()))
                .orElse(null);
    }

    private static BigDecimal value(SelectedValue selected) {
        return selected == null ? null : selected.value();
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private record SelectedValue(BigDecimal value, Instant dataAsOf) {}

    public record MetricPoint(
            String periodType,
            LocalDate periodEnd,
            Integer fiscalYear,
            Integer fiscalQuarter,
            String metricCode,
            BigDecimal value,
            String periodQuality,
            String metricQuality,
            Instant dataAsOf) {
        public MetricPoint(
                String periodType, LocalDate periodEnd, String metricCode, BigDecimal value, Instant dataAsOf) {
            this(
                    periodType,
                    periodEnd,
                    periodEnd.getYear(),
                    ((periodEnd.getMonthValue() - 1) / 3) + 1,
                    metricCode,
                    value,
                    "HEALTHY",
                    "HEALTHY",
                    dataAsOf);
        }
    }

    public record EstimatePoint(LocalDate periodEnd, BigDecimal meanValue, Instant dataAsOf) {}

    public record PointInTimeInputs(Inputs inputs, Instant evidenceDataAsOf) {}

    public record Inputs(
            BigDecimal trailingEps,
            BigDecimal forwardEps,
            BigDecimal revenue,
            BigDecimal freeCashFlow,
            BigDecimal cash,
            BigDecimal totalDebt,
            BigDecimal commonShares) {}
}
