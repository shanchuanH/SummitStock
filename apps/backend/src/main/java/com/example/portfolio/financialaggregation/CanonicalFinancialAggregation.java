package com.example.portfolio.financialaggregation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** One definition of point-in-time TTM: the latest known value for each of the four latest valid quarters. */
public final class CanonicalFinancialAggregation {
    private CanonicalFinancialAggregation() {}

    public static Aggregate ttm(String metricCode, Instant cutoff, List<Observation> observations) {
        var quarters = observations.stream()
                .filter(value -> metricCode.equals(value.metricCode()))
                .filter(value -> "QUARTERLY".equals(value.periodType()))
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .collect(java.util.stream.Collectors.groupingBy(Observation::periodEnd))
                .values()
                .stream()
                .map(versions -> versions.stream()
                        .max(Comparator.comparing(Observation::dataAsOf))
                        .orElseThrow())
                .sorted(Comparator.comparing(Observation::periodEnd).reversed())
                .limit(4)
                .toList();
        if (quarters.size() != 4 || quarters.stream().anyMatch(value -> value.value() == null)) return null;
        return new Aggregate(
                quarters.stream().map(Observation::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                quarters.stream()
                        .map(Observation::dataAsOf)
                        .max(Instant::compareTo)
                        .orElseThrow());
    }

    public record Observation(
            String periodType, LocalDate periodEnd, String metricCode, BigDecimal value, Instant dataAsOf) {}

    public record Aggregate(BigDecimal value, Instant dataAsOf) {}
}
