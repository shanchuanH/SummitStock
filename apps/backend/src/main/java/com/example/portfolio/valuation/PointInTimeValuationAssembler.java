package com.example.portfolio.valuation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public final class PointInTimeValuationAssembler {
    public PointInTimeInputs assemble(
            LocalDate marketDate,
            Instant priceCompletedSessionAvailability,
            List<MetricPoint> metrics,
            List<EstimatePoint> estimates) {
        var cutoff = marketDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var trailingEps = ttm("DILUTED_EPS", cutoff, metrics);
        var forwardEps = forwardEps(marketDate, cutoff, estimates);
        var revenue = ttm("REVENUE", cutoff, metrics);
        var freeCashFlow = ttm("FREE_CASH_FLOW", cutoff, metrics);
        var cash = latest("CASH", cutoff, metrics);
        var totalDebt = latest("TOTAL_DEBT", cutoff, metrics);
        var commonShares = latest("COMMON_SHARES_OUTSTANDING", cutoff, metrics);
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
        var periods = values.stream()
                .filter(value -> value.metricCode().equals(metric))
                .filter(value -> value.periodType().equals("QUARTERLY"))
                .filter(value -> value.dataAsOf().isBefore(cutoff))
                .collect(java.util.stream.Collectors.groupingBy(MetricPoint::periodEnd))
                .entrySet()
                .stream()
                .map(entry -> entry.getValue().stream()
                        .max(Comparator.comparing(MetricPoint::dataAsOf))
                        .orElseThrow())
                .sorted(Comparator.comparing(MetricPoint::periodEnd).reversed())
                .limit(4)
                .toList();
        if (periods.size() != 4) return null;
        return new SelectedValue(
                periods.stream().map(MetricPoint::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                periods.stream()
                        .map(MetricPoint::dataAsOf)
                        .max(Instant::compareTo)
                        .orElseThrow());
    }

    private static SelectedValue latest(String metric, Instant cutoff, List<MetricPoint> values) {
        return values.stream()
                .filter(value -> value.metricCode().equals(metric))
                .filter(value -> value.dataAsOf().isBefore(cutoff))
                .max(Comparator.comparing(MetricPoint::periodEnd).thenComparing(MetricPoint::dataAsOf))
                .map(value -> new SelectedValue(value.value(), value.dataAsOf()))
                .orElse(null);
    }

    private static SelectedValue forwardEps(LocalDate marketDate, Instant cutoff, List<EstimatePoint> values) {
        var earliestPeriod = values.stream()
                .filter(value -> value.periodEnd().compareTo(marketDate) >= 0)
                .filter(value -> value.dataAsOf().isBefore(cutoff))
                .map(EstimatePoint::periodEnd)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (earliestPeriod == null) return null;
        return values.stream()
                .filter(value -> value.periodEnd().equals(earliestPeriod))
                .filter(value -> value.dataAsOf().isBefore(cutoff))
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
            String periodType, LocalDate periodEnd, String metricCode, BigDecimal value, Instant dataAsOf) {}

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
