package com.example.portfolio.valuation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public final class PointInTimeValuationAssembler {
    public Inputs assemble(LocalDate marketDate, List<MetricPoint> metrics, List<EstimatePoint> estimates) {
        var cutoff = marketDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return new Inputs(
                ttm("DILUTED_EPS", cutoff, metrics),
                forwardEps(marketDate, cutoff, estimates),
                ttm("REVENUE", cutoff, metrics),
                ttm("FREE_CASH_FLOW", cutoff, metrics),
                latest("CASH", cutoff, metrics),
                latest("TOTAL_DEBT", cutoff, metrics),
                latest("COMMON_SHARES_OUTSTANDING", cutoff, metrics));
    }

    private static BigDecimal ttm(String metric, Instant cutoff, List<MetricPoint> values) {
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
        return periods.size() == 4
                ? periods.stream().map(MetricPoint::value).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
    }

    private static BigDecimal latest(String metric, Instant cutoff, List<MetricPoint> values) {
        return values.stream()
                .filter(value -> value.metricCode().equals(metric))
                .filter(value -> value.dataAsOf().isBefore(cutoff))
                .max(Comparator.comparing(MetricPoint::periodEnd).thenComparing(MetricPoint::dataAsOf))
                .map(MetricPoint::value)
                .orElse(null);
    }

    private static BigDecimal forwardEps(LocalDate marketDate, Instant cutoff, List<EstimatePoint> values) {
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
                .map(EstimatePoint::meanValue)
                .orElse(null);
    }

    public record MetricPoint(
            String periodType, LocalDate periodEnd, String metricCode, BigDecimal value, Instant dataAsOf) {}

    public record EstimatePoint(LocalDate periodEnd, BigDecimal meanValue, Instant dataAsOf) {}

    public record Inputs(
            BigDecimal trailingEps,
            BigDecimal forwardEps,
            BigDecimal revenue,
            BigDecimal freeCashFlow,
            BigDecimal cash,
            BigDecimal totalDebt,
            BigDecimal commonShares) {}
}
