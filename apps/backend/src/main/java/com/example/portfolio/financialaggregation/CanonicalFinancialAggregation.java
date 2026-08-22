package com.example.portfolio.financialaggregation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** One definition of point-in-time TTM: four continuous fiscal quarters, using only evidence known by cutoff. */
public final class CanonicalFinancialAggregation {
    private CanonicalFinancialAggregation() {}

    public static Aggregate ttm(String metricCode, Instant cutoff, List<Observation> observations) {
        var quarters = observations.stream()
                .filter(value -> metricCode.equals(value.metricCode()))
                .filter(value -> "QUARTERLY".equals(value.periodType()))
                .filter(value -> !value.dataAsOf().isAfter(cutoff))
                .filter(value -> value.fiscalYear() != null && value.fiscalQuarter() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> new FiscalQuarter(value.fiscalYear(), value.fiscalQuarter())))
                .values()
                .stream()
                .map(versions -> versions.stream()
                        .max(Comparator.comparing(Observation::dataAsOf))
                        .orElseThrow())
                .sorted(Comparator.comparing(Observation::fiscalYear)
                        .thenComparing(Observation::fiscalQuarter)
                        .reversed())
                .limit(4)
                .toList();
        if (quarters.size() != 4 || quarters.stream().anyMatch(value -> !value.valid()) || !continuous(quarters)) {
            return null;
        }
        return new Aggregate(
                quarters.stream().map(Observation::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                quarters.stream()
                        .map(Observation::dataAsOf)
                        .max(Instant::compareTo)
                        .orElseThrow());
    }

    private static boolean continuous(List<Observation> quarters) {
        for (int index = 1; index < quarters.size(); index++) {
            var newer = quarters.get(index - 1);
            var older = quarters.get(index);
            var expectedYear = newer.fiscalQuarter() == 1 ? newer.fiscalYear() - 1 : newer.fiscalYear();
            var expectedQuarter = newer.fiscalQuarter() == 1 ? 4 : newer.fiscalQuarter() - 1;
            if (older.fiscalYear() != expectedYear || older.fiscalQuarter() != expectedQuarter) return false;
        }
        return true;
    }

    public record Observation(
            String periodType,
            LocalDate periodEnd,
            Integer fiscalYear,
            Integer fiscalQuarter,
            String metricCode,
            BigDecimal value,
            String periodQuality,
            String metricQuality,
            Instant dataAsOf) {
        public Observation(
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

        boolean valid() {
            return fiscalYear != null
                    && fiscalQuarter != null
                    && fiscalQuarter >= 1
                    && fiscalQuarter <= 4
                    && value != null
                    && dataAsOf != null
                    && "HEALTHY".equals(periodQuality)
                    && "HEALTHY".equals(metricQuality);
        }
    }

    private record FiscalQuarter(int fiscalYear, int fiscalQuarter) {}

    public record Aggregate(BigDecimal value, Instant dataAsOf) {}
}
